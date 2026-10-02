"""Read-through cache for the redirect hot path. Redis is optional: failures degrade to the DB."""

from __future__ import annotations

import logging
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
        # TODO(phase-3 M3): GET KEY_PREFIX + code, decode the JSON {"id","url","expiresAt"} into a CachedLink.
        # Any redis.RedisError -> log a warning and return None (the database is the source of truth).
        raise NotImplementedError("TODO(phase-3 M3): RedisLinkCache.get")

    def put(self, code, link, ttl_seconds):
        # TODO(phase-3 M3): SET KEY_PREFIX + code to that JSON with EX=ttl_seconds; swallow + log RedisError.
        raise NotImplementedError("TODO(phase-3 M3): RedisLinkCache.put")

    def delete(self, code):
        # TODO(phase-3 M3): DEL KEY_PREFIX + code; swallow + log RedisError.
        raise NotImplementedError("TODO(phase-3 M3): RedisLinkCache.delete")

    def status(self):
        try:
            return "UP" if self._redis.ping() else "DOWN"
        except redis.RedisError:
            return "DOWN"
