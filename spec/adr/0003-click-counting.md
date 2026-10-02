# ADR-0003: Count clicks synchronously with one SQL statement

* **Status:** accepted
* **Date:** 2026-10-02

## Context
Each redirect must increment `links.click_count` and today's `click_daily` row (FR-5) without ever
breaking the redirect, and without double counting.

## Options considered
1. **Two statements in the app** (UPDATE, then INSERT/UPDATE) — two round trips, a race between them
   unless wrapped in a transaction.
2. **One statement:** a CTE that `UPDATE … RETURNING id` feeds an `INSERT … ON CONFLICT DO UPDATE` —
   atomic, one round trip, no explicit transaction code in three languages.
3. **Redis `INCR` + periodic flush to Postgres** — fastest, but adds a scheduler, can lose counts on a
   crash, and couples correctness to the optional cache.

## Decision
Option 2. Errors are caught and logged; the visitor is still redirected.

## Consequences
One extra write per redirect — fine at ~12 req/s peak (requirements §7). If we ever need thousands of
redirects/second, revisit with option 3 and an ADR that supersedes this one.
