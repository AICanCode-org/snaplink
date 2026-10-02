from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime


@dataclass
class Link:
    id: int
    code: str
    long_url: str
    custom: bool
    created_at: datetime
    expires_at: datetime | None
    click_count: int = 0

    def is_expired(self, now: datetime) -> bool:
        return self.expires_at is not None and now >= self.expires_at


@dataclass(frozen=True)
class CachedLink:
    """The minimum the redirect path needs. Stored in Redis as JSON."""

    id: int
    long_url: str
    expires_at: datetime | None

    def is_expired(self, now: datetime) -> bool:
        return self.expires_at is not None and now >= self.expires_at


@dataclass(frozen=True)
class DailyClicks:
    day: date
    clicks: int
