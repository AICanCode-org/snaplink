---
phase: 7
slug: phase-7-observability
title: Observability and maintenance
duration: 2.5 h
start_tag: phase-6-end
end_tag: phase-7-end
release: v1.1.0
---
# Phase 7 · Observability and maintenance (v1.1.0)

## 1. Why it matters
Shipping v1.0.0 is the start, not the end. In production you have to answer "is it working right now,
and if not, why?" without attaching a debugger. That needs three things: **logs** you can search,
**metrics** you can graph, and a **request ID** that ties a user's complaint to log lines. Then you
make your first **maintenance change**, a schema migration plus a backward-compatible API field,
and release it safely as v1.1.0.

## 2. Objectives
- Give every request an `X-Request-ID` (reuse a safe incoming one, or generate a UUID) and put it in every log line.
- Emit structured **JSON logs**: one object per line, one access-log line per request.
- Expose `/metrics` in Prometheus text format with five counters that are identical in every track.
- Run a **game day**: stop Redis, observe the metrics and logs, write a short post-mortem.
- Ship a backward-compatible change: `V2__add_last_accessed_at.sql` + `lastAccessedAt` in the API, OpenAPI 1.1.0, CHANGELOG, tag `v1.1.0`.
- Review and merge one Dependabot PR.

## 3. Step-by-step instructions

### Step 7.1 · Request IDs
Rule (shared by all tracks): if the incoming `X-Request-ID` matches `^[A-Za-z0-9._-]{1,64}$`, echo it;
otherwise generate a UUID. Never echo arbitrary input: a header containing a newline could inject
fake log lines or headers.

<!-- track:java -->
A `OncePerRequestFilter` (`web/RequestIdFilter.java`) sets the response header, puts `requestId` in
the SLF4J **MDC** (so every log line in that request carries it) and logs one `snaplink.access` line
with `method`, `path`, `status` and `durationMs` using the fluent API
`LOG.atInfo().addKeyValue("status", …).log()`.
<!-- /track -->

<!-- track:python -->
A Starlette `BaseHTTPMiddleware` (`app/observability.py`) stores the ID in `request.state`, sets the
response header and logs one `snaplink.access` record with `extra={"fields": {...}}`.
<!-- /track -->

<!-- track:typescript -->
Fastify has request IDs built in: `genReqId` (with `requestIdHeader: false`, so we validate the incoming
value ourselves) and a `logController` with `requestIdLogLabel: "requestId"`. An `onSend` hook copies
`request.id` into the `X-Request-ID` response header.
<!-- /track -->

### Step 7.2 · JSON logs
<!-- track:java -->
One property: `logging.structured.format.console=logstash` in `application.properties`. MDC values
and key/value pairs become JSON fields.
<!-- /track -->

<!-- track:python -->
A small `logging.Formatter` subclass (`JsonFormatter`) that `json.dumps` `ts`, `level`, `logger`, `msg`
and the `fields`; installed by `configure_logging()`.
<!-- /track -->

<!-- track:typescript -->
Nothing to add: Fastify's logger (pino) already writes JSON, one line per event (`incoming request`,
`request completed` with `responseTime`).
<!-- /track -->

Check it:
```bash
docker compose --profile <track> up -d --build --wait
curl -s -H 'X-Request-ID: demo-123' localhost:8080/health -o /dev/null -D - | grep -i x-request-id
docker compose logs app-<track> | grep demo-123
```

### Step 7.3 · `/metrics`
Five counters, same names in all tracks (see `GET /metrics` in `spec/openapi.yaml`):
`snaplink_links_created_total`, `snaplink_redirects_total`, `snaplink_cache_hits_total`,
`snaplink_cache_misses_total`, `snaplink_insights_fallback_total`. They are hand-rolled on purpose:
the Prometheus text format is just
```text
# HELP snaplink_redirects_total Successful redirects (302).
# TYPE snaplink_redirects_total counter
snaplink_redirects_total 42
```
In a bigger service you would use Micrometer (Java), `prometheus-client` (Python) or `prom-client` (Node).
Compute the **cache hit ratio** = hits / (hits + misses) after a few redirects.

### Step 7.4 · Game day + post-mortem
1. Generate traffic: create a link and `for i in $(seq 50); do curl -s -o /dev/null localhost:8080/<code>; done`.
2. `docker compose stop redis`. Redirect 50 more times. What happens to latency, `/health`,
   `cache_misses_total`, and the logs?
3. `docker compose start redis`. Does the hit ratio recover by itself?
4. Write `docs/postmortem-<date>-redis-down.md`: summary, impact, timeline, root cause, what went well,
   what to improve, action items. Blameless: describe systems, not people.
5. The reference solution automates steps 1–3 in `scripts/game-day.sh <track>`
   (`git show phase-7-end:scripts/game-day.sh`). Its post-mortem found a real problem in one track:
   read it **after** writing yours (`git show phase-7-end:docs/postmortem-2026-10-02-redis-down.md`).

### Step 7.5 · Maintenance change: `lastAccessedAt` (v1.1.0)
1. `spec/db/migrations/V2__add_last_accessed_at.sql`: `ALTER TABLE links ADD COLUMN IF NOT EXISTS
   last_accessed_at TIMESTAMPTZ NULL;` Nullable, no default rewrite, so old code keeps working.
2. The click statement also sets `last_accessed_at` (the repository now receives the full timestamp,
   not just the day).
3. The `Link` JSON gains an **optional** `lastAccessedAt` (null until the first click). Adding a field is
   backward compatible; removing or renaming one is not.
4. `spec/openapi.yaml` → `version: 1.1.0`; `CHANGELOG.md` → `## [1.1.0]`; bump the track's version.
5. Tests first: an API test with the fake clock (`lastAccessedAt` = the time of the click) and an
   integration test that the column is filled.
6. Release: `git tag -a v1.1.0 -m "SnapLink v1.1.0" && git push origin main --follow-tags`.
   `deploy.yml` publishes `:1.1.0` and moves `:latest`.

### Step 7.6 · Merge a Dependabot PR
Pick one grouped update. Read its release notes, check that CI is green **including integration tests**,
run it locally if it is a major version, then merge. Write down in `AI_LOG.md` or the PR what you checked.

## 4. AI-assist prompts

### Prompt 1: Structured logging setup
```text
In <Spring Boot 4 | FastAPI | Fastify 5> I want one JSON log line per request with requestId, method,
path, status and durationMs, where requestId comes from a validated X-Request-ID header or a new UUID
and appears on every log line of that request. Show the minimal code with no new dependencies, and
explain how the request ID is propagated (MDC / contextvars / child logger).
```
**Verify the output by:** sending a request with `X-Request-ID: demo-123` and with a malicious value
(`$'bad\nInjected: 1'` in bash). The first must be echoed and logged, the second replaced by a UUID.
Check `docker compose logs` shows valid JSON (`| jq .` or `python -m json.tool`).

### Prompt 2: Post-mortem review
```text
Here is my post-mortem draft for a Redis outage on a URL shortener: <paste>. Is it blameless? Are the
action items specific, owned and testable? What evidence (metrics, logs) is missing from the timeline?
```
**Verify the output by:** making sure every timeline entry cites a real log line or metric value you
captured, and every action item can become a GitHub Issue.

### Prompt 3: Is this migration backward compatible?
```text
I run several app instances and deploy one at a time. Is this migration safe to apply while the old
version is still serving traffic? <paste V2 SQL> What would make it unsafe (NOT NULL, defaults on big
tables, renames)? How would I roll it back?
```
**Verify the output by:** running the **v1.0.0** image against a database that already has V2 applied
(`git switch --detach v1.0.0`, `docker compose --profile <track> up -d --build`) and running the v1.0.0
smoke test: it must still pass.

## 5. Deliverables
- [ ] `X-Request-ID` echoed/generated; JSON access logs visible in `docker compose logs`.
- [ ] `/metrics` with the five counters; the smoke test passes (27 checks in v1.1.0).
- [ ] `docs/postmortem-…-redis-down.md` with real numbers from your game day.
- [ ] `V2__add_last_accessed_at.sql`, `lastAccessedAt` in the API, OpenAPI 1.1.0, CHANGELOG, tag `v1.1.0`.
- [ ] One merged Dependabot PR.

## 6. Self-check quiz

### Q1. Why validate an incoming `X-Request-ID` before echoing it?
- [ ] To make IDs shorter
- [x] An unvalidated value could inject newlines or fake fields into logs and headers
- [ ] Browsers reject long headers
- [ ] UUIDs are faster to generate
> **Answer:** Treat every header as untrusted input. A strict allow-list regex is the simplest defence.

### Q2. Which metric tells you Redis is down even when `/health` was not checked?
- [ ] `snaplink_links_created_total` drops
- [x] `snaplink_cache_misses_total` rises while `snaplink_cache_hits_total` stops growing
- [ ] `snaplink_redirects_total` drops to zero
- [ ] `snaplink_insights_fallback_total` rises
> **Answer:** Redirects keep working (from Postgres), so the symptom is a falling hit ratio, which is exactly why you graph it.

### Q3. Why is `ADD COLUMN … NULL` (no default, not `NOT NULL`) the safe choice?
- [ ] Postgres does not support `NOT NULL` columns
- [x] Old app versions that do not know the column keep working, and the change is instant
- [ ] It saves disk space
- [ ] It makes the column indexed
> **Answer:** Expand first, (optionally) contract later. A `NOT NULL` column would break inserts from v1.0.0 during a rolling deploy.

### Q4. Adding `lastAccessedAt` to the response is a … change.
- [ ] Major (breaking)
- [x] Minor (backward-compatible feature), so 1.0.0 → 1.1.0
- [ ] Patch (bug fix)
- [ ] No version change needed
> **Answer:** SemVer: new optional fields are minor. Removing or renaming a field would be major (2.0.0).

### Q5. What makes a post-mortem "blameless"?
- [ ] Nobody writes it
- [ ] It omits the root cause
- [x] It focuses on how the system and process allowed the failure, not on who made a mistake
- [ ] It is kept private
> **Answer:** People hide problems when they fear blame. Blameless reviews produce better action items.

## 7. What you learned
- Correlation IDs, MDC/context propagation and structured JSON logs.
- Prometheus counters, the text exposition format and the cache hit ratio.
- Game days and blameless post-mortems.
- Expand-style, backward-compatible migrations and SemVer minor releases.
- Dependency maintenance with Dependabot, and the role of CI as the safety net.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git switch -c my-phase-8 phase-7-end          # == v1.1.0
git diff v1.0.0 v1.1.0 --stat                 # the whole maintenance change
git diff v1.0.0 v1.1.0 -- spec/               # contract + migration only
```
