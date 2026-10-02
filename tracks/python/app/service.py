"""Business logic. Framework-free so it can be unit-tested with in-memory adapters."""

from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import UTC, datetime

from . import codes
from .cache import LinkCache
from .models import CachedLink, DailyClicks, Link
from .repository import LinkRepository

log = logging.getLogger("snaplink.service")
MAX_CACHE_TTL_SECONDS = 3600
MAX_CODE_ATTEMPTS = 5


def utc_now() -> datetime:
    return datetime.now(UTC)


class LinkService:
    def __init__(
        self,
        repo: LinkRepository,
        cache: LinkCache,
        clock: Callable[[], datetime] = utc_now,
        code_generator: Callable[[], str] = codes.generate_code,
    ):
        self.repo = repo
        self.cache = cache
        self.clock = clock
        self.code_generator = code_generator

    # ---- create (M2) ----------------------------------------------------
    def create(self, url: object, alias: object = None, expires_in_days: object = None) -> Link:
        # TODO(phase-3 M2): validate the three inputs, compute expires_at from self.clock(),
        # insert with the alias (DuplicateCodeError -> errors.alias_taken) or with a random code,
        # retrying up to MAX_CODE_ATTEMPTS times on DuplicateCodeError (birthday problem, ADR-0001).
        raise NotImplementedError("TODO(phase-3 M2): LinkService.create")

    # ---- read / delete (M2) -------------------------------------------------
    def get(self, code: str) -> Link:
        # TODO(phase-3 M2): repo.find_by_code, raise errors.not_found(code) when missing.
        raise NotImplementedError("TODO(phase-3 M2): LinkService.get")

    def delete(self, code: str) -> None:
        # TODO(phase-3 M2): delete from the repo (404 if missing) AND evict it from the cache.
        raise NotImplementedError("TODO(phase-3 M2): LinkService.delete")

    # ---- redirect hot path (M2 + M3) -------------------------------------------
    def resolve(self, code: str) -> str:
        # TODO(phase-3 M2/M3): cache-aside lookup (cache -> repo -> cache.put with _cache_ttl),
        # 404 if unknown, 410 if expired (also evict it), then record a click. A failing
        # record_click must be logged and must NOT break the redirect. Return the long URL.
        raise NotImplementedError("TODO(phase-3 M2): LinkService.resolve")

    @staticmethod
    def _cache_ttl(link: CachedLink, now: datetime) -> int:
        # TODO(phase-3 M3): MAX_CACHE_TTL_SECONDS, but never longer than the time left until expiry.
        raise NotImplementedError("TODO(phase-3 M3): LinkService._cache_ttl")

    # ---- analytics (M4) ---------------------------------------------------------
    def stats(self, code: str, days: object = None) -> tuple[Link, list[DailyClicks]]:
        # TODO(phase-3 M4): validate_days, get the link, return (link, self.daily_series(link, window)).
        raise NotImplementedError("TODO(phase-3 M4): LinkService.stats")

    def daily_series(self, link: Link, days: int) -> list[DailyClicks]:
        # TODO(phase-3 M4): one DailyClicks per UTC day for the last `days` days (today included),
        # oldest first, zero-filled for days without clicks (repo.daily_clicks only returns non-zero days).
        raise NotImplementedError("TODO(phase-3 M4): LinkService.daily_series")
