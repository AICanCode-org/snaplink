# SnapLink architecture

> Reference solution for Phase 2 (tag `phase-2-end`). Diagrams are [Mermaid](https://mermaid.js.org/);
> GitHub renders them, and so do most IDEs and the aicancode.org lesson viewer.

## 1. Context diagram
```mermaid
flowchart LR
    creator([Link creator<br/>curl / app / Postman]) -- "create, stats, insights<br/>JSON over HTTPS" --> snaplink[[SnapLink]]
    visitor([Visitor's browser]) -- "GET /{code}" --> snaplink
    snaplink -- "302 Location" --> visitor
    snaplink -. "optional: summarise stats" .-> llm[(LLM endpoint<br/>Ollama local / Gemini / Groq)]
```
SnapLink **never fetches the target URL** (no SSRF surface) and stores no personal data (NFR-3, NFR-4).

## 2. Container diagram
```mermaid
flowchart TB
    subgraph laptop["docker compose (your laptop)  ·  or Render + Neon + Upstash (optional Phase 6)"]
        app["SnapLink app<br/>Java 21 · Spring Boot<br/>or Python · FastAPI<br/>or TypeScript · Fastify<br/>:8080"]
        pg[("PostgreSQL 16<br/>links, click_daily,<br/>schema_migrations")]
        redis[("Redis 7<br/>snaplink:link:{code}<br/>TTL ≤ 1 h")]
        ollama[("Ollama (optional)<br/>llama3.2:1b")]
    end
    client([HTTP clients]) --> app
    app -- "SQL (source of truth)" --> pg
    app -- "cache-aside, 500 ms timeout" --> redis
    app -. "OpenAI-compatible /chat/completions,<br/>5 s timeout, falls back to template" .-> ollama
```
* **Hot path:** `GET /{code}` → Redis → (miss) Postgres → one SQL statement to count the click.
* **Every track is a drop-in replacement** for the others: same port, env vars, schema and contract.

## 3. Redirect sequence (cache-aside)
```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant A as App
    participant R as Redis
    participant P as Postgres
    B->>A: GET /aB3dE9x
    A->>R: GET snaplink:link:aB3dE9x
    alt cache hit
        R-->>A: {id, url, expiresAt}
    else miss (or Redis down / timeout → treated as miss)
        A->>P: SELECT ... WHERE code = $1
        alt not found
            A-->>B: 404 problem+json
        end
        P-->>A: row
        A->>R: SET ... EX min(3600, seconds until expiry)
    end
    alt expired (checked on hits too)
        A->>R: DEL snaplink:link:aB3dE9x
        A-->>B: 410 Gone
    else active
        A->>P: UPDATE links + UPSERT click_daily (one statement)
        Note over A,P: failure is logged, never breaks the redirect
        A-->>B: 302 Location: long URL, Cache-Control: no-store
    end
```

## 4. Data model
```mermaid
erDiagram
    links ||--o{ click_daily : "has (ON DELETE CASCADE)"
    links {
        BIGSERIAL id PK
        VARCHAR32 code UK "uq_links_code"
        TEXT long_url "CHECK length <= 2048"
        BOOLEAN is_custom
        TIMESTAMPTZ created_at
        TIMESTAMPTZ expires_at "nullable, partial index"
        BIGINT click_count
    }
    click_daily {
        BIGINT link_id PK, FK
        DATE day PK "UTC"
        BIGINT clicks
    }
```
* `uq_links_code` is the **source of truth for uniqueness**: we insert and retry on a unique violation
  instead of "check, then insert" (which races).
* `click_daily` is pre-aggregated (one row per link per day) so stats are a tiny range scan, and no
  per-visitor data is stored (NFR-4).
* Schema: [`db/migrations/V1__init.sql`](db/migrations/V1__init.sql).

## 5. Key decisions
* [ADR-0001](adr/0001-short-code-generation.md) — random Base62 codes + retry on collision
* [ADR-0002](adr/0002-302-redirects.md) — `302` + `no-store` instead of `301`
* [ADR-0003](adr/0003-click-counting.md) — synchronous single-statement click counting in Postgres
* Shared contract: [`openapi.yaml`](openapi.yaml) (problem+json errors, ISO-8601 UTC timestamps with milliseconds)
