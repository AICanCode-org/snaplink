"""Persistence. LinkRepository is the port; Postgres and in-memory are adapters."""

from __future__ import annotations

import threading
from datetime import date, datetime
from typing import Protocol

from psycopg_pool import ConnectionPool

from .errors import DuplicateCodeError
from .models import DailyClicks, Link


class LinkRepository(Protocol):
    def insert(
        self, code: str, long_url: str, custom: bool, created_at: datetime, expires_at: datetime | None
    ) -> Link: ...
    def find_by_code(self, code: str) -> Link | None: ...
    def delete(self, code: str) -> bool: ...
    def record_click(self, link_id: int, day: date) -> None: ...
    def daily_clicks(self, link_id: int, start: date, end: date) -> list[DailyClicks]: ...
    def ping(self) -> bool: ...


_COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count"


def _to_link(row: dict) -> Link:
    return Link(
        id=row["id"],
        code=row["code"],
        long_url=row["long_url"],
        custom=row["is_custom"],
        created_at=row["created_at"],
        expires_at=row["expires_at"],
        click_count=row["click_count"],
    )


class PostgresLinkRepository:
    def __init__(self, pool: ConnectionPool):
        self._pool = pool

    def insert(self, code, long_url, custom, created_at, expires_at) -> Link:
        # TODO(phase-3 M2): INSERT ... RETURNING {_COLUMNS}; map psycopg's UniqueViolation to DuplicateCodeError.
        raise NotImplementedError("TODO(phase-3 M2): PostgresLinkRepository.insert")

    def find_by_code(self, code) -> Link | None:
        # TODO(phase-3 M2): SELECT {_COLUMNS} FROM links WHERE code = %s
        # (hint: `from psycopg.rows import dict_row`, cursor(row_factory=dict_row), then _to_link).
        raise NotImplementedError("TODO(phase-3 M2): PostgresLinkRepository.find_by_code")

    def delete(self, code) -> bool:
        # TODO(phase-3 M2): DELETE ... and return True if a row was deleted (click_daily cascades).
        raise NotImplementedError("TODO(phase-3 M2): PostgresLinkRepository.delete")

    def record_click(self, link_id, day) -> None:
        # TODO(phase-3 M3): ONE atomic statement that bumps links.click_count and upserts
        # click_daily(link_id, day) (hint: a CTE + INSERT ... ON CONFLICT DO UPDATE).
        raise NotImplementedError("TODO(phase-3 M3): PostgresLinkRepository.record_click")

    def daily_clicks(self, link_id, start, end) -> list[DailyClicks]:
        # TODO(phase-3 M4): rows of click_daily for link_id with start <= day <= end, ordered by day.
        raise NotImplementedError("TODO(phase-3 M4): PostgresLinkRepository.daily_clicks")

    def ping(self) -> bool:
        try:
            with self._pool.connection(timeout=2) as conn:
                conn.execute("SELECT 1")
            return True
        except Exception:  # noqa: BLE001 - health checks must never raise
            return False


class InMemoryLinkRepository:
    """Test double with the same behaviour as Postgres (unique codes, cascade delete)."""

    def __init__(self):
        self._lock = threading.Lock()
        self._links: dict[str, Link] = {}
        self._clicks: dict[tuple[int, date], int] = {}
        self._next_id = 1

    def insert(self, code, long_url, custom, created_at, expires_at) -> Link:
        with self._lock:
            if code in self._links:
                raise DuplicateCodeError(code)
            link = Link(self._next_id, code, long_url, custom, created_at, expires_at, 0)
            self._next_id += 1
            self._links[code] = link
            return Link(**vars(link))

    def find_by_code(self, code) -> Link | None:
        link = self._links.get(code)
        return Link(**vars(link)) if link else None

    def delete(self, code) -> bool:
        with self._lock:
            link = self._links.pop(code, None)
            if link is None:
                return False
            self._clicks = {k: v for k, v in self._clicks.items() if k[0] != link.id}
            return True

    def record_click(self, link_id, day) -> None:
        with self._lock:
            for link in self._links.values():
                if link.id == link_id:
                    link.click_count += 1
                    self._clicks[(link_id, day)] = self._clicks.get((link_id, day), 0) + 1
                    return

    def daily_clicks(self, link_id, start, end) -> list[DailyClicks]:
        return sorted(
            (DailyClicks(d, c) for (lid, d), c in self._clicks.items() if lid == link_id and start <= d <= end),
            key=lambda dc: dc.day,
        )

    def ping(self) -> bool:
        return True
