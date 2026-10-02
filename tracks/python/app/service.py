"""Business logic. Framework-free so it can be unit-tested with in-memory adapters."""

from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import UTC, date, datetime, timedelta

from . import codes
from .cache import LinkCache
from .errors import DuplicateCodeError, alias_taken, expired, not_found
from .models import CachedLink, DailyClicks, Link
from .repository import LinkRepository
from .validation import validate_alias, validate_days, validate_expires_in_days, validate_url

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

    # ---- create -------------------------------------------------------
    def create(self, url: object, alias: object = None, expires_in_days: object = None) -> Link:
        long_url = validate_url(url)
        custom_alias = validate_alias(alias)
        days = validate_expires_in_days(expires_in_days)
        now = self.clock()
        expires_at = now + timedelta(days=days) if days else None

        if custom_alias:
            try:
                return self.repo.insert(custom_alias, long_url, True, now, expires_at)
            except DuplicateCodeError:
                raise alias_taken(custom_alias) from None

        # Random codes can collide (birthday problem); the DB unique constraint is the
        # source of truth, so we retry a few times instead of "check then insert".
        for _ in range(MAX_CODE_ATTEMPTS):
            try:
                return self.repo.insert(self.code_generator(), long_url, False, now, expires_at)
            except DuplicateCodeError:
                log.info("short code collision, retrying")
        raise RuntimeError("could not generate a unique short code")

    # ---- read / delete --------------------------------------------------
    def get(self, code: str) -> Link:
        link = self.repo.find_by_code(code)
        if link is None:
            raise not_found(code)
        return link

    def delete(self, code: str) -> None:
        if not self.repo.delete(code):
            raise not_found(code)
        self.cache.delete(code)

    # ---- redirect hot path ----------------------------------------------
    def resolve(self, code: str) -> str:
        now = self.clock()
        cached = self.cache.get(code)
        if cached is None:
            link = self.repo.find_by_code(code)
            if link is None:
                raise not_found(code)
            cached = CachedLink(id=link.id, long_url=link.long_url, expires_at=link.expires_at)
            ttl = self._cache_ttl(cached, now)
            if ttl > 0:
                self.cache.put(code, cached, ttl)
        if cached.is_expired(now):
            self.cache.delete(code)
            raise expired(code)
        try:
            self.repo.record_click(cached.id, now.date())
        except Exception:  # noqa: BLE001 - a lost click must never break a redirect
            log.exception("failed to record click for %s", code)
        return cached.long_url

    @staticmethod
    def _cache_ttl(link: CachedLink, now: datetime) -> int:
        """A cache entry must never outlive the link's expiry."""
        if link.expires_at is None:
            return MAX_CACHE_TTL_SECONDS
        remaining = int((link.expires_at - now).total_seconds())
        return min(MAX_CACHE_TTL_SECONDS, remaining)

    # ---- analytics ------------------------------------------------------
    def stats(self, code: str, days: object = None) -> tuple[Link, list[DailyClicks]]:
        window = validate_days(days)
        link = self.get(code)
        return link, self.daily_series(link, window)

    def daily_series(self, link: Link, days: int) -> list[DailyClicks]:
        end = self.clock().date()
        start = end - timedelta(days=days - 1)
        by_day = {dc.day: dc.clicks for dc in self.repo.daily_clicks(link.id, start, end)}
        return [DailyClicks(day=d, clicks=by_day.get(d, 0)) for d in _date_range(start, end)]


def _date_range(start: date, end: date):
    current = start
    while current <= end:
        yield current
        current += timedelta(days=1)
