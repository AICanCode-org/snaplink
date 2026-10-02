---
phase: 4
slug: phase-4-testing
title: Testing (unit, integration, contract, coverage)
duration: 4 h
start_tag: phase-3-end
end_tag: phase-4-end
---
# Phase 4 · Testing: integration tests, failure tests and a coverage gate

## 1. Why it matters
Unit tests with in-memory fakes prove your logic. They cannot prove that your SQL is valid, that the
unique constraint really fires, that `ON DELETE CASCADE` works or that the app survives a dead Redis.
Integration tests against **real** PostgreSQL and Redis do. A coverage gate then stops the suite from
quietly rotting. Together they let you change code (or accept an AI's refactor) without fear.

## 2. Objectives
- Explain the test pyramid for SnapLink: unit (fast, many), integration (real dependencies), smoke/contract (black-box, few).
- Run the integration suite against the Compose services using a **separate** test database.
- Prove failure behaviour: Redis down, an expiry stored in the database, cascading deletes.
- Enforce ≥ 85 % line coverage locally and understand what the number does and does not mean.

## 3. Step-by-step instructions

### Step 4.1 · Know the layers you already have
| Layer | Where | Needs | Run |
|---|---|---|---|
| Unit | `…/unit/…`, in-memory repo + cache, fake clock | nothing | `make test` |
| Integration | `…/integration/…`, real Postgres + Redis | `docker compose up -d postgres redis` | `make test-integration` |
| Smoke (black-box) | `scripts/smoke-test.sh` (Phase 6) | a running app | `scripts/smoke-test.sh` |

The integration tests use `TEST_DATABASE_URL` (default `…/snaplink_test`) and `TEST_REDIS_URL`
(default `redis://localhost:6379/1`), so they **truncate test data, never your dev data**.

### Step 4.2 · Turn the integration tests on
Delete the last line of your pending list (the integration entry), then:
```bash
docker compose up -d postgres redis
make test-integration
```

<!-- track:java -->
Java: `ApiIntegrationTest` is tagged `@Tag("integration")` and excluded by default; `-Pintegration`
selects it (`mvn verify -Pintegration`). It boots the **whole** Spring app on a random port
(`@SpringBootTest(webEnvironment = RANDOM_PORT)`) and calls it with `java.net.http.HttpClient`, which
does not follow redirects, so a `302` stays visible. `@DynamicPropertySource` points the app at the test DB.
<!-- /track -->

<!-- track:python -->
Python: tests marked `pytest.mark.integration` are deselected by default (`addopts` in
`pyproject.toml`); `make test-integration` runs `pytest -m integration`. The app is created with
`create_app(Settings(...))` pointing at the test DB, and `TestClient(..., follow_redirects=False)`.
<!-- /track -->

<!-- track:typescript -->
TypeScript: integration tests live in `test/integration/` and run with `npm run test:integration`
(vitest). The suite builds the real Fastify app with `PgLinkRepository` + `RedisLinkCache` and uses
`app.inject()`; `beforeEach` truncates tables and flushes Redis DB 1.
<!-- /track -->

### Step 4.3 · Read the failure tests
Open the integration file and find these cases. Each maps to a requirement:
- **unique alias enforced by the database** (FR-2): two inserts, second gets `409`.
- **expired in the database → 410** (FR-3): the test sets `expires_at` in the past with SQL.
- **delete cascades analytics and cache** (FR-6): `click_daily` count is 0 and the Redis key is gone.
- **Redis down degrades gracefully** (NFR-2): a cache pointed at port 6390 (nothing listens) reports
  `DOWN` and returns misses instead of throwing.
- **migrations recorded** (`schema_migrations` contains version 1).

Then do the game-day version by hand: `docker compose stop redis`, redirect a link, `curl /health`,
`docker compose start redis`.

### Step 4.4 · Coverage gate
```bash
make coverage        # unit + integration, fails below 85 % line coverage
```

<!-- track:java -->
JaCoCo: `mvn verify -Pcoverage`; HTML report in `target/site/jacoco/index.html`; the rule is in the
`coverage` profile of `pom.xml`.
<!-- /track -->

<!-- track:python -->
pytest-cov: `--cov-fail-under=85` in the Makefile; `coverage.xml` is written for CI; add
`--cov-report=html` for a browsable report.
<!-- /track -->

<!-- track:typescript -->
@vitest/coverage-v8: `thresholds: { lines: 85 }` in `vitest.config.ts`; report in `coverage/`.
<!-- /track -->

Look at the uncovered lines. Are they untested **behaviour** (write a test) or genuinely
unreachable glue (fine)? 100 % is not the goal. Tested behaviour is.

### Step 4.5 · Commit
```bash
git switch -c test/phase-4-integration
git commit -am "test: enable integration suite and coverage gate"
```

## 4. AI-assist prompts

### Prompt 1: Missing integration scenarios
```text
These are my integration tests for a URL shortener backed by PostgreSQL and Redis: <paste test names>.
These are my requirements: <paste FR/NFR>. Which requirements are only covered by unit tests with
fakes, where a fake could hide a real bug (SQL, constraints, timeouts, serialization)? Propose up to 3
new integration tests with their exact assertions.
```
**Verify the output by:** writing one of the proposed tests, making it fail on purpose (break the SQL
or drop the constraint in a scratch DB), and confirming it fails for the right reason before you restore the code.

### Prompt 2: Is this test flaky?
```text
This test sometimes fails in CI but passes locally: <paste test + failure output>. List the likely
sources of non-determinism (time, ordering, shared state, ports, async) and how to make the test
deterministic without adding sleeps.
```
**Verify the output by:** running the test 20 times in a loop (`for i in $(seq 20); do make test-integration || break; done`)
before and after the change.

## 5. Deliverables
- [ ] Pending list empty; `make test-integration` passes against Compose services.
- [ ] `make coverage` passes (≥ 85 %), with a note in the PR on what the uncovered lines are.
- [ ] Manual game day: Redis stopped, redirects still `302`, `/health` shows `cache: DOWN`.
- [ ] `AI_LOG.md`: one entry about a test an assistant suggested and how you validated it.

## 6. Self-check quiz

### Q1. Why do integration tests use the `snaplink_test` database?
- [ ] Postgres allows only one connection per database
- [x] The tests truncate tables between cases; a separate DB protects your dev data
- [ ] The schema is different
- [ ] It is faster
> **Answer:** Isolation. The same idea applies to Redis: tests use logical DB 1, the app uses DB 0.

### Q2. Which bug can ONLY an integration test catch here?
- [ ] An off-by-one in the trend calculation
- [x] A typo in the click-counting SQL statement
- [ ] A wrong error message for a reserved alias
- [ ] A wrong default for `days`
> **Answer:** Unit tests use the in-memory repository, so the SQL never runs there. The other bugs live in pure logic.

### Q3. How does the "Redis down" test simulate an outage without stopping a container?
- [x] It points a cache client at a port where nothing listens (6390)
- [ ] It mocks the Redis library
- [ ] It sets a TTL of zero
- [ ] It flushes the database
> **Answer:** A closed port behaves like a dead server (connection refused), using the real client and its real timeouts.

### Q4. Your coverage is 92 %. What does that prove?
- [ ] The code has no bugs
- [ ] All requirements are implemented
- [x] 92 % of lines executed during the tests. It says nothing about whether the assertions are meaningful
- [ ] The integration tests are not needed
> **Answer:** Coverage finds *untested* code; it cannot find *wrong* tests. Use it as a floor, not a target.

### Q5. Why does the Java integration test use an HTTP client that does not follow redirects?
- [ ] Following redirects is slow
- [x] It must assert the `302` status and `Location` header, not the content of the target site
- [ ] Spring forbids it
- [ ] The target URL does not exist
> **Answer:** Following the redirect would test aicancode.org, not SnapLink, and would need the internet.

## 7. What you learned
- The test pyramid and what each layer can and cannot prove.
- Real-dependency integration tests with isolated databases and clean state per test.
- Testing failure modes on purpose: dead cache, expired rows, cascades, constraints.
- Coverage gates as a safety net, and their limits.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git switch -c my-phase-5 phase-4-end
git diff phase-3-end phase-4-end          # just the pending-list change: the tests were there all along
```
