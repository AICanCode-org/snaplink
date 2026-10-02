# SnapLink requirements

> Reference solution for Phase 1 (tag `phase-1-end`). Yours can differ — what matters is that every
> requirement is **testable** and every story has **acceptance criteria**.

## 1. Problem statement
Creators, students and small teams share long URLs (course pages, forms, docs) in chats, slides and
posters. Long links break across lines, look untrustworthy and give no feedback on whether anyone
clicked. Hosted shorteners lock analytics behind paid plans or track visitors aggressively.
**SnapLink** is a small, self-hostable shortener with honest, privacy-friendly click counts and a
one-paragraph weekly insight.

## 2. Users / personas
* **Priya, campus club lead** — shares event links on posters; wants short, memorable aliases and to
  know which channel worked.
* **Arjun, indie developer** — embeds links in docs and READMEs; wants an API, expiry for temporary
  links and zero vendor lock-in.
* **Operator (you)** — runs SnapLink on a laptop or a free tier; wants it cheap, observable and
  boring to operate.

## 3. Functional requirements (FR)
| ID | Requirement |
|---|---|
| FR-1 | The system shall create a short link for an `http`/`https` URL of at most 2048 characters with a host and no whitespace; anything else is rejected with `400` (problem+json). |
| FR-2 | The system shall generate a random 7-character Base62 code unless the client supplies an alias matching `^[A-Za-z0-9_-]{3,32}$`; reserved words (`api, health, metrics, docs, admin, static`) are rejected; a taken alias returns `409`. |
| FR-3 | A link may expire after `expiresInDays` (integer 1–365). Expired links return `410 Gone` and are never served from the cache. |
| FR-4 | `GET /{code}` shall redirect with `302` and `Cache-Control: no-store`; unknown codes return `404`. |
| FR-5 | Every successful redirect shall increment the link's total and its per-day (UTC) counter. A failure to count must not break the redirect. |
| FR-6 | The system shall return link details (`GET /api/v1/links/{code}`) and delete links (`DELETE`, `204`; analytics are deleted too). |
| FR-7 | The system shall return daily click stats for the last *N* days (1–90, default 7), zero-filled, oldest first. |
| FR-8 | `GET /health` shall report database and cache status; `503` when the database is down, `200` (cache `DOWN`) when only Redis is down. |
| FR-9 | `GET /api/v1/links/{code}/insights` shall return a one-paragraph summary of the last 7 days. The default **template** generator is deterministic and works offline: busiest day (earliest wins ties) and trend (last half vs first half of the window). An optional OpenAI-compatible LLM (Ollama locally, or a free Gemini/Groq key) may be enabled; on any LLM error the response falls back to the template with `fallback: true`. |

## 4. Non-functional requirements (NFR)
| ID | Category | Requirement (measurable) |
|---|---|---|
| NFR-1 | Performance | p95 server-side redirect latency < 50 ms on a laptop with a warm cache (measured in Phase 7). |
| NFR-2 | Availability | Redirects keep working when Redis is down (degrade to Postgres), proven by an integration test. |
| NFR-3 | Security | Only `http`/`https` targets; SnapLink never fetches the target URL (no SSRF); secrets only via environment variables; containers run as non-root. |
| NFR-4 | Privacy | No IP addresses, user agents or cookies are stored — only per-day counts. |
| NFR-5 | Cost | Runs entirely on a student laptop with Docker; the optional cloud deploy uses only free tiers. |
| NFR-6 | Portability | Identical behaviour in the Java, Python and TypeScript tracks, verified by the shared smoke test. |
| NFR-7 | Quality | ≥ 85 % line coverage per track; CI green before merge. |

## 5. User stories + acceptance criteria
**US-1 Create a short link** — *As Arjun, I want to POST a long URL and get a short one, so that I can paste it anywhere.*
* Given a valid URL, when I POST `/api/v1/links`, then I get `201`, a `Location` header and a 7-char `code`.
* Given `ftp://x`, when I POST it, then I get `400` with `Content-Type: application/problem+json`.

**US-2 Custom alias** — *As Priya, I want a memorable alias like `/techfest`, so that people can type it from a poster.*
* Given a free alias, the link is created with `custom: true`.
* Given a taken alias, I get `409 alias-taken`; given `admin`, I get `400`.

**US-3 Expiring links** — *As Priya, I want links for a registration form to stop working after the deadline.*
* Given `expiresInDays: 7`, `expiresAt` is 7 days after `createdAt`.
* Given an expired link, `GET /{code}` returns `410` even if it was cached.

**US-4 Redirect** — *As a visitor, I want the short link to take me to the page instantly.*
* `GET /{code}` returns `302` with `Location` = the long URL and `Cache-Control: no-store`.
* Unknown codes return `404` problem+json.

**US-5 Click stats** — *As Priya, I want to see clicks per day, so that I know which poster drop worked.*
* After 2 redirects today, `/stats?days=7` returns 7 entries, the last one with `clicks: 2`.

**US-6 Delete** — *As Arjun, I want to delete a link I shared by mistake.*
* `DELETE` returns `204`; afterwards the code returns `404` (also from the cache) and its stats are gone.

**US-7 Health** — *As the operator, I want a health endpoint, so that Docker/Render can restart a broken instance.*
* With Redis stopped, `/health` is `200` with `cache: DOWN` and redirects still work.

**US-8 Weekly insight (AI feature)** — *As Priya, I want a one-paragraph summary, so that I don't have to read charts.*
* Without any LLM configured the summary comes from the template (`provider: template`).
* With an unreachable LLM, the endpoint still answers `200` with `fallback: true`.

## 6. Out of scope (v1)
User accounts and auth, a web dashboard UI, QR codes, link editing, geo/device analytics, bulk import,
rate limiting (noted as a Phase 8 "next step").

## 7. Back-of-the-envelope estimates (illustrative capacity for a single small instance)
* 1,000 new links/day, read:write ≈ 100:1 → 100,000 redirects/day ≈ **1.2 req/s average**, ~12 req/s peak (10×).
* Row size ≈ 0.5 KB → 1,000 × 365 × 0.5 KB ≈ **180 MB/year** of links (+ a few bytes per link per active day in `click_daily`).
* 7-char Base62 = 62⁷ ≈ 3.5 × 10¹² codes; with 1 M links the chance that a new random code collides is ≈ 1 M / 3.5 T ≈ 3 × 10⁻⁷, so "retry up to 5 times on a unique violation" is plenty (ADR-0001).
* Conclusion: one container + one small Postgres + optional Redis is enough; the cache is about latency and resilience practice, not raw scale.
