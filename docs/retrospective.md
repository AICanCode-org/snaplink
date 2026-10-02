# SnapLink retrospective (reference solution, v1.0.0 → v1.1.0)

Format: *What went well / What didn't / What I'd change*, plus one technical, one process and one AI
lesson, as asked in Phase 8. Written by the maintainers who built the reference solution. Yours will
(and should) look different.

## What went well
- **One spec, three implementations.** `spec/openapi.yaml`, the SQL migrations and
  `scripts/smoke-test.sh` are shared. All three tracks pass the same 27-check smoke test against their
  Docker images, which turns NFR-6 ("identical behaviour") from a promise into a check.
- **Tests before code with a pending list.** Turning on one test group at a time kept every commit green
  and made "done" objective. Every phase tag builds and passes its tests in all three tracks
  (`scripts/verify-tags.sh`).
- **Boring, explicit infrastructure.** Plain SQL migrations, a hand-rolled `/metrics`, and Compose
  profiles meant no framework magic to debug, and identical behaviour on every laptop.
- **Offline first.** The template fallback for AI insights meant nobody needed an API key to finish,
  and the "LLM unreachable" path is tested, not hoped for.

## What didn't go well
- **Failure tests were too kind.** The Phase 4 "Redis down" test used a closed port, which fails in
  milliseconds. The Phase 7 game day stopped the container instead, and the Python track went from ~4 ms
  to **~16 s per redirect** because DNS lookups are not covered by socket timeouts
  (see `docs/postmortem-2026-10-02-redis-down.md`). Correct behaviour, unacceptable latency.
- **Logging consistency drifted.** One track wrote cache warnings with `console.warn` (plain text) while
  everything else was JSON. Only reading real container logs revealed it.
- **Version churn.** Framework majors (Spring Boot 4 / Jackson 3, Fastify 5, Vitest 3) moved APIs between
  "what the assistant remembered" and "what the docs say today". Several snippets had to be rewritten
  against current documentation.

## What I'd change next time
1. Write a **degraded-mode latency budget** next to NFR-2 on day one, and test it with the realistic failure.
2. Put the **game day into CI** (stop Redis inside the Docker smoke job and assert latency).
3. Add a log-format check to the smoke test (every line of `docker compose logs app-*` must parse as JSON).
4. Pin and record exact framework versions in the README to reduce "works in the docs I read" surprises.

## Three lessons
- **Technical:** cache TTL must respect link expiry (`min(1 h, expiresAt - now)`), and a cache is only
  "optional" if its *failure* is fast. Correctness and latency are separate properties; test both.
- **Process:** a pending-test list plus small milestone commits made progress visible and reviews small.
  The catch-up tags made it safe to fall behind.
- **AI:** assistants were excellent at boilerplate (Dockerfiles, workflow skeletons, test scaffolding) and
  confidently wrong on version-specific details: deprecated options, renamed packages, and a deploy-hook
  query parameter that had to be checked against the provider's docs. Every accepted suggestion is in
  `AI_LOG.md` with how it was verified; tests and actionlint caught what reading did not.

## Evidence
| | Java | Python | TypeScript |
|---|---|---|---|
| Unit + integration tests (v1.1.0) | 75 | 88 | 85 |
| Line coverage (gate ≥ 85 %) | 91.2 % (JaCoCo) | 96.8 % (coverage.py) | 95.9 % (V8) |
| Docker smoke test v1.1.0 | 27/27 | 27/27 | 27/27 |
| Redis-stopped redirect latency (game day) | 6.9 ms | 16 s → one ≈ 8 s probe per 5 s, ≈ 3 ms otherwise | 3.5 ms |
