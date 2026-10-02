"""Read-through cache for the redirect hot path. Redis is optional: failures degrade to the DB."""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Callable
from datetime import datetime
from typing import Protocol

import redis

from .models import CachedLink

log = logging.getLogger("snaplink.cache")
KEY_PREFIX = "snaplink:link:"


class LinkCache(Protocol):
    def get(self, code: str) -> CachedLink | None: ...
    def put(self, code: str, link: CachedLink, ttl_seconds: int) -> None: ...
    def delete(self, code: str) -> None: ...
    def status(self) -> str: ...  # "UP" | "DOWN" | "DISABLED"


class NoopCache:
    def get(self, code):
        return None

    def put(self, code, link, ttl_seconds):
        pass

    def delete(self, code):
        pass

    def status(self):
        return "DISABLED"


class InMemoryCache:
    """Test double. TTL is ignored on purpose: tests control time through the service clock."""

    def __init__(self):
        self.store: dict[str, CachedLink] = {}

    def get(self, code):
        return self.store.get(code)

    def put(self, code, link, ttl_seconds):
        self.store[code] = link

    def delete(self, code):
        self.store.pop(code, None)

    def status(self):
        return "UP"


class RedisLinkCache:
    """Redis-backed cache with a tiny circuit breaker.

    After a failed call, get/put skip Redis for ``cooldown_seconds`` and behave like a cache miss. Without it,
    every request during an outage pays the full failure cost. The Phase 7 game day measured ~16 s per redirect
    when the Redis container was stopped: resolving the hostname "redis" took ~8 s per call, and socket timeouts
    do not cover DNS (docs/postmortem-2026-10-02-redis-down.md). Deletes are always attempted.
    """

    def __init__(
        self,
        url: str,
        cooldown_seconds: float = 5.0,
        clock: Callable[[], float] = time.monotonic,
        client: redis.Redis | None = None,
    ):
        self._redis = client or redis.Redis.from_url(
            url, socket_timeout=0.5, socket_connect_timeout=0.5, decode_responses=True
        )
        self._cooldown = cooldown_seconds
        self._clock = clock
        self._open_until = 0.0

    def _closed(self) -> bool:
        return self._clock() >= self._open_until

    def _trip(self, message: str, exc: Exception) -> None:
        self._open_until = self._clock() + self._cooldown
        log.warning("%s: %s (skipping Redis for %.0f s)", message, exc, self._cooldown)

    def get(self, code):
        if not self._closed():
            return None
        try:
            raw = self._redis.get(KEY_PREFIX + code)
        except redis.RedisError as exc:
            self._trip("cache get failed, falling back to database", exc)
            return None
        if raw is None:
            return None
        data = json.loads(raw)
        expires = datetime.fromisoformat(data["expiresAt"]) if data["expiresAt"] else None
        return CachedLink(id=data["id"], long_url=data["url"], expires_at=expires)

    def put(self, code, link, ttl_seconds):
        if not self._closed():
            return
        payload = json.dumps(
            {"id": link.id, "url": link.long_url, "expiresAt": link.expires_at.isoformat() if link.expires_at else None}
        )
        try:
            self._redis.set(KEY_PREFIX + code, payload, ex=ttl_seconds)
        except redis.RedisError as exc:
            self._trip("cache put failed", exc)

    def delete(self, code):
        try:
            self._redis.delete(KEY_PREFIX + code)
        except redis.RedisError as exc:
            self._trip("cache delete failed", exc)

    def status(self):
        try:
            up = bool(self._redis.ping())
        except redis.RedisError:
            return "DOWN"
        if up:
            self._open_until = 0.0  # Redis is back: close the circuit immediately
        return "UP" if up else "DOWN"
