"""Read-through cache for the redirect hot path. Redis is optional: failures degrade to the DB."""

from __future__ import annotations

import json
import logging
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
    def __init__(self, url: str):
        self._redis = redis.Redis.from_url(url, socket_timeout=0.5, socket_connect_timeout=0.5, decode_responses=True)

    def get(self, code):
        try:
            raw = self._redis.get(KEY_PREFIX + code)
        except redis.RedisError as exc:
            log.warning("cache get failed, falling back to database: %s", exc)
            return None
        if raw is None:
            return None
        data = json.loads(raw)
        expires = datetime.fromisoformat(data["expiresAt"]) if data["expiresAt"] else None
        return CachedLink(id=data["id"], long_url=data["url"], expires_at=expires)

    def put(self, code, link, ttl_seconds):
        payload = json.dumps(
            {"id": link.id, "url": link.long_url, "expiresAt": link.expires_at.isoformat() if link.expires_at else None}
        )
        try:
            self._redis.set(KEY_PREFIX + code, payload, ex=ttl_seconds)
        except redis.RedisError as exc:
            log.warning("cache put failed: %s", exc)

    def delete(self, code):
        try:
            self._redis.delete(KEY_PREFIX + code)
        except redis.RedisError as exc:
            log.warning("cache delete failed: %s", exc)

    def status(self):
        try:
            return "UP" if self._redis.ping() else "DOWN"
        except redis.RedisError:
            return "DOWN"
