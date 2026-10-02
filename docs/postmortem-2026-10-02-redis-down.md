# Post-mortem: Redis outage game day (2026-10-02)

| | |
|---|---|
| Type | Planned game day (fault injection), local Docker Compose stack, v1.1.0 release candidate |
| Fault | `docker compose stop redis` while redirecting, then `docker compose start redis` |
| Tracks | Python, TypeScript, Java (one at a time, same stack) |
| Status | Resolved. One fix shipped in v1.1.0, two follow-ups open |
| Format | Blameless: we describe systems and decisions, not people |

All times are UTC. Numbers come from `scripts/game-day.sh <track>`: 50 redirects of one link per phase,
client-side `curl` timings, `/health` and `/metrics` after each phase. Reproduce it yourself, but expect your
own numbers, especially for DNS.

## Summary
With Redis stopped, all three tracks kept redirecting correctly (`302`, NFR-2 held), and `/health`
reported `cache: DOWN` with overall `UP`. However, the **Python** track became unusably slow:
**~16 s per redirect**. Root cause: when the Redis container is stopped, its hostname `redis` no longer
resolves inside the Compose network, and on this machine each failed DNS lookup took **~8 s**.
Socket connect/read timeouts (0.5 s) do not cover DNS resolution, and the Python client retried the
lookup on every cache call (get + put = 2 lookups). TypeScript (ioredis) and Java (Lettuce) keep a
connection state machine and failed fast. We added a small circuit breaker to the Python cache and
measured again: the outage now costs one slow request per 5-second window.

## Impact (before the fix)
| Track | Baseline avg (Redis up) | Redis stopped | `/health` with Redis stopped |
|---|---|---|---|
| Python | 3.58 ms | **16.01 s** per redirect | 200 `cache: DOWN`, **8.0 s** |
| TypeScript | 3.81 ms | 4.46 ms (10 requests) | 200 `cache: DOWN` |
| Java | 6.68 ms | 6.89 ms (10 requests) | 200 `cache: DOWN` |

No data loss and no wrong redirects: Redis is only a cache, and click counting writes to PostgreSQL.
In a real deployment, a 16 s redirect would be equivalent to an outage for users.

## Timeline (Python track)
| Time (UTC) | Event | Evidence |
|---|---|---|
| 05:05:48 | Test link `tSMWo34` created | `201` |
| 05:05:49 | Baseline: 50 redirects, all `302`, avg 3.58 ms | `cache_hits_total 49`, `cache_misses_total 1` |
| 05:05:49 | **Fault injected:** `docker compose stop redis` | |
| 05:05:49+ | Redirect burst stalls | no output for minutes |
| ~05:13 | Burst aborted after ~7 min (≈ 26 requests). A single timed redirect: `302` after **16.01 s**; `/health` after **8.0 s** | `curl -w %{time_total}` |
| ~05:13 | Diagnosis: inside the container, `socket.getaddrinfo("redis", 6379)` fails with *Temporary failure in name resolution* after **8.03 s** | one-line Python probe in `docker compose exec` |
| 05:14–05:15 | Same game day on TypeScript and Java (10 redirects during the outage): no latency impact | script output |
| 05:15 | Fix: circuit breaker in `RedisLinkCache` (Python) + 5 unit tests; 88 tests pass, coverage 96.84 % | `make coverage` |
| 05:16:25 | Re-test after the fix: 50 redirects with Redis stopped, all `302`, **avg 164 ms** (one ≈ 8 s request, the rest ≈ 3 ms) | log: `cache get failed … (skipping Redis for 5 s)` |
| 05:16:43 | `docker compose start redis`. 50 redirects avg 3.63 ms, hit ratio recovers by itself | `cache_hits_total` 49 → 99 |

## Root cause
1. `docker compose stop redis` removes the container from Compose's DNS. Lookups of `redis` are then
   forwarded to the host's upstream resolver, which on the game-day machine answered only after ~8 s.
   (On another machine this can be milliseconds; on a misconfigured network it can be worse.)
2. redis-py's `socket_connect_timeout` starts **after** name resolution. DNS is a blocking
   `getaddrinfo` call with no timeout we control.
3. redis-py opens a new connection attempt for every command while Redis is unreachable, so every
   `get` and every `put` paid the 8 s again.
4. The Phase 4 "Redis down" integration test pointed the cache at a closed **port** on `localhost`
   (connection refused within milliseconds), so it proved *correctness* (redirects still work) but not
   *latency* under a realistic failure mode.

## What went well
- Graceful degradation worked in all tracks: no `5xx`, correct redirects, counts kept in PostgreSQL.
- `/health` distinguished *degraded* (`cache: DOWN`, still `200`) from *down*.
- `/metrics` made the impact visible: `cache_misses_total` rose by exactly the number of requests.
- Structured JSON logs (v1.1) gave one warning line with the precise error text
  (`Error -3 connecting to redis:6379. Temporary failure in name resolution`).
- Same smoke test + same game day across three stacks quickly showed the problem was specific to one client library.

## What went wrong
- No latency budget for degraded mode was written down, so nobody tested for it.
- Our failure test simulated the *easy* failure (refused port), not the *likely* one (host disappears / hangs).
- The TypeScript track logged cache warnings through `console.warn` (plain text), breaking the
  one-JSON-line-per-event rule. Fixed in v1.1.0 by passing Fastify's logger to the cache.

## Action items
| # | Action | Type | Status |
|---|---|---|---|
| 1 | Python: circuit breaker in `RedisLinkCache` (skip Redis for 5 s after a failure; a successful health ping closes it) + `tests/unit/test_cache.py` | fix | **Done in v1.1.0** |
| 2 | TypeScript: route cache warnings through the Fastify (pino) JSON logger | fix | **Done in v1.1.0** |
| 3 | Add NFR-8: *"With Redis unavailable, p95 redirect latency stays < 100 ms (excluding one probe per 5 s)"* | requirement | Open |
| 4 | Add a game-day script to CI (`docker compose stop redis` inside the Docker smoke job) asserting latency | test | Open |
| 5 | `/health` can still take ~8 s per call on the DNS failure path (Python): report the breaker state instead of pinging while open | fix | Open |
| 6 | Consider resolving Redis once at start-up or using an IP/`extra_hosts` in Compose. Rejected for now: hides real DNS behaviour in the cloud | idea | Won't do |

## Lessons
- **Timeouts have gaps:** check *every* phase of a network call: DNS, connect, TLS, read.
- **Test the failure you will actually get.** "Port closed" and "host gone" behave very differently.
- **Run the same experiment across implementations.** The two tracks that failed fast told us immediately what "good" looks like.
