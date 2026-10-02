"""Persistence. LinkRepository is the port; Postgres and in-memory are adapters."""

from __future__ import annotations

import threading
from datetime import UTC, date, datetime
from typing import Protocol

from psycopg import errors as pg_errors
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from .errors import DuplicateCodeError
from .models import DailyClicks, Link


class LinkRepository(Protocol):
    def insert(
        self, code: str, long_url: str, custom: bool, created_at: datetime, expires_at: datetime | None
    ) -> Link: ...
    def find_by_code(self, code: str) -> Link | None: ...
    def delete(self, code: str) -> bool: ...
    def record_click(self, link_id: int, at: datetime) -> None: ...
    def daily_clicks(self, link_id: int, start: date, end: date) -> list[DailyClicks]: ...
    def ping(self) -> bool: ...


_COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count, last_accessed_at"


def _to_link(row: dict) -> Link:
    return Link(
        id=row["id"],
        code=row["code"],
        long_url=row["long_url"],
        custom=row["is_custom"],
        created_at=row["created_at"],
        expires_at=row["expires_at"],
        click_count=row["click_count"],
        last_accessed_at=row["last_accessed_at"],
    )


class PostgresLinkRepository:
    def __init__(self, pool: ConnectionPool):
        self._pool = pool

    def insert(self, code, long_url, custom, created_at, expires_at) -> Link:
        try:
            with self._pool.connection() as conn, conn.cursor(row_factory=dict_row) as cur:
                cur.execute(
                    f"INSERT INTO links (code, long_url, is_custom, created_at, expires_at) "
                    f"VALUES (%s, %s, %s, %s, %s) RETURNING {_COLUMNS}",
                    (code, long_url, custom, created_at, expires_at),
                )
                return _to_link(cur.fetchone())
        except pg_errors.UniqueViolation as exc:
            raise DuplicateCodeError(code) from exc

    def find_by_code(self, code) -> Link | None:
        with self._pool.connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            cur.execute(f"SELECT {_COLUMNS} FROM links WHERE code = %s", (code,))
            row = cur.fetchone()
            return _to_link(row) if row else None

    def delete(self, code) -> bool:
        with self._pool.connection() as conn:
            return conn.execute("DELETE FROM links WHERE code = %s", (code,)).rowcount > 0

    def record_click(self, link_id, at) -> None:
        # One statement = one atomic unit: bump the total, remember when, upsert the daily bucket.
        with self._pool.connection() as conn:
            conn.execute(
                """
                WITH bumped AS (
                    UPDATE links SET click_count = click_count + 1, last_accessed_at = %(at)s
                    WHERE id = %(id)s RETURNING id
                )
                INSERT INTO click_daily (link_id, day, clicks)
                SELECT id, %(day)s, 1 FROM bumped
                ON CONFLICT (link_id, day) DO UPDATE SET clicks = click_daily.clicks + 1
                """,
                {"id": link_id, "day": at.astimezone(UTC).date(), "at": at},
            )

    def daily_clicks(self, link_id, start, end) -> list[DailyClicks]:
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT day, clicks FROM click_daily WHERE link_id = %s AND day BETWEEN %s AND %s ORDER BY day",
                (link_id, start, end),
            ).fetchall()
            return [DailyClicks(day=r[0], clicks=r[1]) for r in rows]

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

    def record_click(self, link_id, at) -> None:
        day = at.astimezone(UTC).date()
        with self._lock:
            for link in self._links.values():
                if link.id == link_id:
                    link.click_count += 1
                    link.last_accessed_at = at
                    self._clicks[(link_id, day)] = self._clicks.get((link_id, day), 0) + 1
                    return

    def daily_clicks(self, link_id, start, end) -> list[DailyClicks]:
        return sorted(
            (DailyClicks(d, c) for (lid, d), c in self._clicks.items() if lid == link_id and start <= d <= end),
            key=lambda dc: dc.day,
        )

    def ping(self) -> bool:
        return True
