# ADR-0002: 302 + Cache-Control: no-store for redirects

* **Status:** accepted
* **Date:** 2026-10-02

## Context
Browsers cache `301 Moved Permanently` aggressively — often forever. That would make expiry (FR-3),
deletion (FR-6) and click counting (FR-5) silently stop working for returning visitors.

## Options considered
1. **301** — fewer requests to us, but we lose control and analytics.
2. **302 Found** (+ `Cache-Control: no-store`) — every click reaches SnapLink.
3. **307/308** — preserve the HTTP method; irrelevant for GET links and less familiar to students.

## Decision
`302` with `Cache-Control: no-store`.

## Consequences
Each click costs one request (cheap: cache-aside, NFR-1). Expiry and deletion take effect immediately.
