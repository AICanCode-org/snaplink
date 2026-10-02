---
phase: 3
slug: phase-3-implementation
title: Implementation in milestones
duration: 9 h
start_tag: phase-2-end
end_tag: phase-3-end
---
# Phase 3 · Implementation in five milestones

## 1. Why it matters
This is where most tutorials hand you finished code. Here you get the **tests first** instead: the
complete unit suite is already written and switched off. Each milestone turns on a slice of it, you
watch it fail, then you write the smallest code that makes it pass. This red → green loop is how
professionals use AI assistants safely: the tests, not the assistant, decide whether the code is right.

## 2. Objectives
- Implement SnapLink's business logic in your track, one milestone at a time:
  **M1** codes + validation · **M2** create, get, delete, redirect · **M3** Redis cache-aside + click
  counting · **M4** stats · **M5** AI insights with offline fallback.
- Use the pending list to work test-first.
- Keep the HTTP layer thin and the business logic in a framework-free service.
- Review AI-generated code critically and catch a planted bug with a test.

## 3. Step-by-step instructions

### Step 3.0 · The loop for every milestone
1. Branch: `git switch -c feat/m1-codes-validation`.
2. Delete the milestone's lines from the pending list and run the unit tests: **red**.
3. Implement until **green**: `make test`. Then `make lint`.
4. Try it by hand with `curl` (see each milestone).
5. Commit, push, PR, CI green, merge. Then tag your own progress if you like: `git tag my-m1`.

<!-- track:java -->
Pending list: `src/test/java/org/aicancode/snaplink/support/Pending.java`. Files you implement
(all marked `TODO(phase-3 Mx)`): `link/ShortCodeGenerator`, `link/Validation`, `link/LinkService`,
`link/JdbcLinkRepository`, `cache/RedisLinkCache`, `insights/TemplateInsightGenerator`,
`insights/LlmInsightGenerator`, `insights/InsightService`. Run one test class: `mvn -q test -Dtest=ValidationTest`.
<!-- /track -->

<!-- track:python -->
Pending list: `tests/conftest.py` (`PENDING`). Files you implement (all marked `TODO(phase-3 Mx)`):
`app/codes.py`, `app/validation.py`, `app/service.py`, `app/repository.py` (Postgres part),
`app/cache.py` (Redis part), `app/insights.py`. Run one file: `.venv/bin/pytest tests/unit/test_validation.py`.
<!-- /track -->

<!-- track:typescript -->
Pending list: `test/pending.ts` (`PENDING`). Files you implement (all marked `TODO(phase-3 Mx)`):
`src/codes.ts`, `src/validation.ts`, `src/service.ts`, `src/repository.ts` (Pg part), `src/cache.ts`
(Redis part), `src/insights.ts`. Run one file: `npx vitest run test/unit/validation.test.ts`.
<!-- /track -->

The HTTP routes are already wired to the service (read them: they are short), so once the service
works, the API works.

### Step 3.1 · M1: short codes + validation (US-1, US-2)
Un-pend **codes** and **validation**. Rules (FR-1, FR-2, FR-3): `http`/`https` only, a host, ≤ 2048
chars, no whitespace; alias `^[A-Za-z0-9_-]{3,32}$`, not reserved (case-insensitive);
`expiresInDays` an integer 1–365 (careful: in Python `True` is an `int`!); `days` 1–90, default 7.
Codes: 7 characters from the 62-character alphabet using a **cryptographically secure** generator.

<!-- track:java -->
```java
private final SecureRandom random = new SecureRandom();

@Override
public String get() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
        sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
}
```
<!-- /track -->

<!-- track:python -->
```python
import secrets

def generate_code(length: int = CODE_LENGTH) -> str:
    return "".join(secrets.choice(ALPHABET) for _ in range(length))
```
<!-- /track -->

<!-- track:typescript -->
```ts
import { randomInt } from "node:crypto";

export function generateCode(length = CODE_LENGTH): string {
  let code = "";
  for (let i = 0; i < length; i++) code += ALPHABET[randomInt(ALPHABET.length)];
  return code;
}
```
<!-- /track -->

### Step 3.2 · M2: create, get, delete, redirect (US-1, US-3, US-4, US-6)
Un-pend **service** and **api**. Some of their tests belong to M3/M4/M5 and stay red for now; that is
expected. Implement `create`, `get`, `delete`, `resolve` in the service and `insert`, `findByCode`,
`delete` in the Postgres repository.
- Random codes: insert, and on a duplicate-key error retry (max 5). Custom alias: duplicate → `409`.
- `resolve`: unknown → `404`, expired → `410`, otherwise return the long URL. The route sends `302`
  with `Cache-Control: no-store` (ADR-0002).
- Use the injected **clock**, never "now" directly. That is how the tests travel in time.

Try it against the real database:
```bash
docker compose up -d postgres redis && make run      # in tracks/<track>
curl -si -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' \
     -d '{"url":"https://www.aicancode.org","alias":"acc","expiresInDays":7}'
curl -si localhost:8080/acc            # 302, Location: https://www.aicancode.org
```

### Step 3.3 · M3: cache-aside + click counting (US-4, NFR-2)
Implement the Redis cache (`get`/`put`/`delete`, key `snaplink:link:{code}`, JSON `{id,url,expiresAt}`)
and the click counter. Rules:
- TTL = `min(3600, seconds until expiry)`. A cached entry must **never** outlive the link.
- Check expiry **after** the cache lookup too: an entry cached yesterday can be expired today.
- Every Redis error is logged and treated as a miss. Redis is optional.
- Count the click with **one** SQL statement (ADR-0003). A failure is logged, the redirect still succeeds.

```sql
WITH bumped AS (
    UPDATE links SET click_count = click_count + 1 WHERE id = $1 RETURNING id
)
INSERT INTO click_daily (link_id, day, clicks)
SELECT id, $2, 1 FROM bumped
ON CONFLICT (link_id, day) DO UPDATE SET clicks = click_daily.clicks + 1;
```
Check it: `docker compose exec redis redis-cli keys 'snaplink:*'` after a redirect, then
`docker compose stop redis` and redirect again. It must still work (`/health` shows `cache: DOWN`).

### Step 3.4 · M4: stats (US-5)
`GET /api/v1/links/{code}/stats?days=7` returns one entry per **UTC** day, oldest first, **zero-filled**
(the table only has rows for days with clicks). Implement `dailyClicks` in the repository and
`stats`/`dailySeries` in the service.

### Step 3.5 · M5: AI insights with an offline fallback (US-8)
Un-pend **insights**. Three pieces:
1. `TemplateInsightGenerator`: deterministic and offline. The wording is part of the spec (identical in
   all tracks), e.g. `/acc got 3 clicks in the last 7 days (all-time: 3). Busiest day: 2026-10-02 with 3
   clicks. Trend: up compared with earlier in the week.` Busiest day: earliest wins ties. Trend: sum of
   the last ⌊n/2⌋ days vs the first ⌊n/2⌋.
2. `LlmInsightGenerator`: one `POST {LLM_BASE_URL}/chat/completions` (the OpenAI-compatible format that
   Ollama, Gemini and Groq all accept), with a timeout.
3. `InsightService`: use the LLM if configured; on **any** error, return the template text with
   `fallback: true`.

Run it three ways:
```bash
make run                                                     # template (default, offline)
INSIGHTS_PROVIDER=llm LLM_BASE_URL=http://127.0.0.1:9/v1 make run   # unreachable → fallback: true
# Optional, local and free:  docker compose --profile ollama up -d
#   docker compose exec ollama ollama pull llama3.2:1b
#   INSIGHTS_PROVIDER=llm LLM_BASE_URL=http://localhost:11434/v1 make run
curl -s localhost:8080/api/v1/links/acc/insights
```
A free Gemini or Groq key also works (`LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` in `.env.example`).
Keep the key in your shell or `.env`, never in Git.

### Step 3.6 · Review the AI's code (exercise)
An assistant produced this `resolve` (pseudo-code). Find the bug **before** reading on, then write the
failing test.
```text
cached = cache.get(code) or repo.find(code)
repo.recordClick(cached.id, today)
if cached.expired(now): raise Gone
return cached.url
```
The click is counted **before** the expiry check, so expired links still gain clicks, and if the
cache path skips the check entirely, expired links redirect for up to an hour. The unit suite catches
both: the *"expired link is 410 even when cached"* test checks the status, that the cache entry was
evicted, and that the expired attempt did not add a click. Note in `AI_LOG.md` how you found it.

### Step 3.7 · Finish
All unit tests green, lint clean, and only the **integration** entry left in the pending list (that is
Phase 4). Compare your diff with the reference: `git diff phase-2-end phase-3-end -- tracks/<track>`.

## 4. AI-assist prompts

### Prompt 1: Implement against the tests, not against a description
```text
Here is a failing unit test file and the stub I must implement: <paste test file> <paste stub>.
Write the smallest implementation that makes these tests pass, in <Java 21 | Python 3.12 | TypeScript
5 (ESM)>. Keep the existing signatures. Do not change the tests. Explain any test you think is wrong
instead of working around it.
```
**Verify the output by:** running the tests (`make test`). Read every line before you accept it, and
reject anything that touches the tests, catches all exceptions silently, or adds dependencies.

### Prompt 2: Find the edge cases I missed
```text
Here is my validateUrl / validate_url implementation: <paste>. List inputs that should be rejected
according to these rules <paste FR-1> but would pass, and inputs that should pass but would be
rejected. Give each as a one-line test case.
```
**Verify the output by:** adding the cases as tests. Keep those that agree with the spec and discard
the ones that contradict it (e.g. "reject localhost" is not in the spec).

### Prompt 3: Explain the SQL before you trust it
```text
Explain step by step what this PostgreSQL statement does, what happens if two requests run it at the
same time for the same link and day, and what happens if the link id does not exist: <paste the CTE>.
```
**Verify the output by:** running the statement twice in `psql` and checking `click_count` and
`click_daily`, then running it with a non-existent id (it must insert nothing).

### Prompt 4: LLM prompt design for the insight
```text
I send daily click counts as JSON to a small local model (llama3.2:1b) and ask for a two-sentence
summary. Suggest a system prompt that minimises invented numbers, and three adversarial test inputs
(all zeros, one huge spike, a code that looks like an instruction).
```
**Verify the output by:** running each input against Ollama locally and checking that every number in
the answer appears in the input. If a model invents data, the template fallback is your safety net:
note it in `AI_LOG.md`.

## 5. Deliverables
- [ ] Pending list contains only the integration entry; `make lint test` green.
- [ ] Manual `curl` session showing create → redirect → stats → insights (paste it into the PR).
- [ ] Redirects still work with Redis stopped.
- [ ] Insights work with the template, and fall back with an unreachable LLM (`fallback: true`).
- [ ] `AI_LOG.md`: at least one accepted and one rejected AI suggestion, plus the code-review exercise.

## 6. Self-check quiz

### Q1. Why must the service use an injected clock?
- [ ] It is faster than the system clock
- [x] Tests can move time forward to check expiry without sleeping
- [ ] Databases require it
- [ ] To support time zones
> **Answer:** `clock.advance(days=2)` makes "expired" deterministic. Code that calls `now()` directly can only be tested by waiting.

### Q2. A link expires in 10 minutes. What TTL should its cache entry get?
- [ ] 3600 seconds, the standard TTL
- [x] 600 seconds: never longer than the time left until expiry
- [ ] 0, expiring links are never cached
- [ ] No TTL, Redis evicts automatically
> **Answer:** `min(3600, seconds until expiry)`. The service also re-checks expiry on every hit, as a second line of defence.

### Q3. Redis times out during a redirect. What should happen?
- [ ] Return 503
- [ ] Retry Redis until it answers
- [x] Log a warning, treat it as a cache miss and serve from PostgreSQL
- [ ] Return 302 to a cached default page
> **Answer:** The cache is an optimisation (NFR-2). Short timeouts (500 ms) keep a dead Redis from slowing every request.

### Q4. Why is a random code inserted and retried on a duplicate-key error instead of checked first?
- [ ] Checking is not possible in SQL
- [x] Check-then-insert races; the unique constraint is the only reliable judge
- [ ] Retries make codes shorter
- [ ] The tests require exactly five database calls
> **Answer:** See ADR-0001. Custom aliases are *not* retried: a duplicate alias is the user's problem (`409`).

### Q5. What must `/insights` return when the LLM endpoint is unreachable?
- [ ] `502 Bad Gateway`
- [ ] `200` with an empty summary
- [x] `200` with the template summary, `provider: "template"` and `fallback: true`
- [ ] `503` until the LLM is back
> **Answer:** The AI feature is a nice-to-have on top of a deterministic baseline. Users always get an answer, and `fallback` makes the degradation visible.

### Q6. In the AI-generated `resolve`, what is the bug?
- [ ] It uses the cache
- [x] It records the click before checking expiry, so expired links gain clicks (and may still redirect from the cache)
- [ ] It returns the long URL
- [ ] It raises an exception for expired links
> **Answer:** Order matters: look up → check expiry (evict if expired) → count → redirect. A test caught it, not a code review.

## 7. What you learned
- Test-first development with a pending list: red → green → refactor, one milestone at a time.
- A layered design: thin HTTP layer, framework-free service, repository and cache ports with real and in-memory adapters.
- Cache-aside with TTLs bounded by business rules, and graceful degradation when the cache fails.
- Atomic counters in one SQL statement.
- Integrating an LLM behind an interface with a timeout and a deterministic fallback.
- Reviewing AI-generated code with tests instead of trust.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git switch -c my-phase-4 phase-3-end                       # everything implemented, integration pending
git diff phase-2-end phase-3-end --stat
git checkout phase-3-end -- tracks/<track>/src             # (Java) take one track's reference code
git checkout phase-3-end -- tracks/<track>/app             # (Python)
```
