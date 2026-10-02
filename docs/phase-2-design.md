---
phase: 2
slug: phase-2-design
title: Design (architecture, DB schema, API contract)
duration: 3 h
start_tag: phase-1-end
end_tag: phase-2-end
---
# Phase 2 · Design: architecture, database schema, API contract

## 1. Why it matters
Design is where you make the expensive decisions on purpose: how codes are generated, what is cached,
where the source of truth lives, what happens when something fails. Writing them down as diagrams
and **Architecture Decision Records (ADRs)** lets a reviewer follow your reasoning in minutes, and
lets future-you remember *why*. The SQL schema is the one artefact that is shared by all three
tracks, so a mistake here costs three times.

## 2. Objectives
- Draw context, container and sequence diagrams in Mermaid (rendered by GitHub).
- Write three ADRs: short-code generation, redirect status code, click counting.
- Design the PostgreSQL schema and write it as migration `spec/db/migrations/V1__init.sql`.
- Read the given OpenAPI contract and map each endpoint to a requirement.
- Watch your track's migrator apply V1 on start-up.

## 3. Step-by-step instructions

### Step 2.1 · Diagrams in `spec/architecture.md`
Mermaid is text, so it diffs and reviews like code. Start with a container diagram:
```text
flowchart TB
    client([HTTP clients]) --> app["SnapLink app :8080"]
    app -- "SQL (source of truth)" --> pg[("PostgreSQL")]
    app -- "cache-aside, 500 ms timeout" --> redis[("Redis")]
    app -. "optional, falls back to template" .-> llm[("LLM endpoint")]
```
Wrap it in a code fence with the language `mermaid` and preview it on GitHub or in VS Code (extension
"Markdown Preview Mermaid Support"). Then draw the **redirect sequence**: cache hit, cache miss,
unknown code, expired link, and Redis down.

### Step 2.2 · ADRs
Copy `spec/adr/0000-template.md` three times:
1. `0001-short-code-generation.md`: sequence + Base62 vs hash vs random CSPRNG + retry. Use your Phase 1 collision estimate.
2. `0002-302-redirects.md`: `301` vs `302` vs `307`, and what browser caching does to analytics.
3. `0003-click-counting.md`: two statements vs one atomic statement vs Redis `INCR` + periodic flush.

Each ADR is one page: context, options with pros/cons, decision, consequences.

### Step 2.3 · Schema → `spec/db/migrations/V1__init.sql`
Requirements to satisfy:
- `links`: surrogate `BIGSERIAL` id, unique `code` (≤ 32 chars), `long_url` ≤ 2048 enforced by a
  `CHECK`, `is_custom`, `created_at`, nullable `expires_at`, `click_count`.
- A **partial index** on `expires_at` for rows that can expire.
- `click_daily(link_id, day, clicks)` with a composite primary key and `ON DELETE CASCADE`.

Name the unique constraint (`uq_links_code`) so that error messages and tests can refer to it. Never
edit a migration after it is merged. Add `V2__…` instead (you will do that in Phase 7).

### Step 2.4 · Watch the migrator apply it
Every track runs the same tiny migrator at start-up (≈ 40 lines, no Flyway/Alembic, so the three tracks
can share one SQL file). Start your app (`make run`) and inspect the result:
```bash
docker compose exec postgres psql -U snaplink -d snaplink -c '\d links' -c 'select * from schema_migrations'
```

<!-- track:java -->
Java: `tracks/java/src/main/java/org/aicancode/snaplink/db/Migrator.java`, called from the `migrator`
bean in `config/AppConfig.java`. The `linkRepository` bean depends on it, so Spring cannot serve
traffic against an old schema.
<!-- /track -->

<!-- track:python -->
Python: `tracks/python/app/db.py` → `run_migrations()`, called in the FastAPI `lifespan` in `app/main.py`.
<!-- /track -->

<!-- track:typescript -->
TypeScript: `tracks/typescript/src/db.ts` → `runMigrations()`, awaited in `src/index.ts` before `listen()`.
<!-- /track -->

### Step 2.5 · Map the contract
For every path in `spec/openapi.yaml`, write the FR number next to it (in a table in
`architecture.md` or as a comment in your notes). Any endpoint without a requirement, or any
requirement without an endpoint, is a design bug: fix one of the two. Validate the file:
```bash
pipx run openapi-spec-validator spec/openapi.yaml       # or: npx @redocly/cli lint spec/openapi.yaml
```

### Step 2.6 · Commit
```bash
git switch -c docs/phase-2-design
git add spec/
git commit -m "docs(spec): architecture, ADRs 0001-0003, V1 schema"
```

## 4. AI-assist prompts

### Prompt 1: Mermaid sequence diagram
```text
Write a Mermaid sequenceDiagram for GET /{code} in a URL shortener with participants Browser, App,
Redis, Postgres. Use alt blocks for: cache hit, cache miss, unknown code (404), expired link (410,
also delete the cache key), and Redis timeout (treated as a miss). Clicks are recorded with one SQL
statement after the expiry check, and a failure to record must not break the redirect.
```
**Verify the output by:** pasting it into https://mermaid.live (it must render without errors) and
walking through each `alt` branch against FR-3, FR-4, FR-5 and NFR-2 in your requirements.

### Prompt 2: Review my schema
```text
Review this PostgreSQL 16 migration for a URL shortener: <paste V1__init.sql>. Requirements: <paste
FR-1..FR-7>. Point out missing constraints, wrong types, missing or useless indexes, and anything that
would make "delete a link and its analytics" or "count a click atomically" hard. Do not add tables for
features that are out of scope.
```
**Verify the output by:** running the migration (`make run`, then `\d links`) and trying the failure
cases by hand in `psql`: insert a duplicate code (must fail with `uq_links_code`), a 3,000-character URL
(must fail the CHECK), delete a link that has `click_daily` rows (they must disappear).

### Prompt 3: Steel-man the option I rejected
```text
For a URL shortener with ~12 requests/second at peak, argue as convincingly as you can FOR counting
clicks with Redis INCR and a periodic flush to Postgres, then list the failure modes. Keep it under
200 words.
```
**Verify the output by:** checking each claimed failure mode against your architecture (what happens on
a crash between flushes? when Redis is down?), and record the decision and the trade-off in ADR-0003.

## 5. Deliverables
- [ ] `spec/architecture.md` with context, container, redirect-sequence and ER diagrams that render on GitHub.
- [ ] `spec/adr/0001…0003` accepted.
- [ ] `spec/db/migrations/V1__init.sql`; the app applies it on start-up (`schema_migrations` has version 1).
- [ ] Every OpenAPI path mapped to a requirement.
- [ ] Compare: `git diff phase-1-end phase-2-end --stat`.

## 6. Self-check quiz

### Q1. Why is uniqueness of `code` enforced by a database constraint instead of "SELECT first, then INSERT"?
- [ ] SELECTs are slow
- [x] Two concurrent requests can both see "free" and both insert. Only the constraint is race-free
- [ ] The ORM requires it
- [ ] Constraints use less disk space
> **Answer:** Check-then-act is a race. Insert, catch the unique violation, and retry (random code) or return `409` (custom alias).

### Q2. What does the partial index `… WHERE expires_at IS NOT NULL` buy us?
- [x] An index only over rows that can expire, so it stays small
- [ ] It makes the column nullable
- [ ] It deletes expired rows automatically
- [ ] It speeds up redirects by code
> **Answer:** Most links never expire; indexing only the others keeps the index small for future clean-up jobs.

### Q3. Why does `click_daily` reference `links(id)` with `ON DELETE CASCADE`?
- [ ] To make inserts faster
- [x] Deleting a link must delete its analytics too (FR-6), without extra application code
- [ ] Postgres requires it for composite keys
- [ ] So that Redis is cleared
> **Answer:** The database guarantees there are no orphan rows. The app still has to evict the cache entry itself.

### Q4. Why do all three tracks use a hand-written migrator instead of Flyway / Alembic / node-pg-migrate?
- [ ] Those tools are not free
- [x] One set of plain SQL files is shared by all tracks; a 40-line migrator behaves identically in each
- [ ] They do not support PostgreSQL 16
- [ ] They cannot run in Docker
> **Answer:** It is a teaching trade-off: one schema, three languages. In a single-language team you would normally pick the ecosystem's tool.

### Q5. Which statement about an ADR is true?
- [ ] It is written after the code, to document what was built
- [x] It records the context, the options considered and the consequences of one decision
- [ ] It replaces the README
- [ ] It must be approved by a manager
> **Answer:** ADRs are short and specific. When a decision changes, a new ADR *supersedes* the old one rather than editing history.

## 7. What you learned
- Diagrams-as-code with Mermaid: context, containers, sequences, ER.
- Writing ADRs that show trade-offs, not just choices.
- Schema design with named constraints, CHECKs, partial indexes and cascades.
- Versioned, immutable migrations and how an app applies them safely (advisory lock, one transaction per file).
- Contract-first design: the OpenAPI file is the agreement between tracks.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git switch -c my-phase-3 phase-2-end
git diff phase-1-end phase-2-end -- spec/        # the reference design
git checkout phase-2-end -- spec/db/migrations/V1__init.sql   # take only the schema
```
