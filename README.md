# SnapLink: build a production-style URL shortener with AI as your pair programmer

[![CI](https://github.com/AICanCode-org/snaplink/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AICanCode-org/snaplink/actions/workflows/ci.yml)
[![CI (solution)](https://github.com/AICanCode-org/snaplink/actions/workflows/ci.yml/badge.svg?branch=solution)](https://github.com/AICanCode-org/snaplink/actions/workflows/ci.yml?query=branch%3Asolution)

## Case study (reference solution)

> **SnapLink shortens URLs, counts clicks and explains a link's traffic in plain English. It is
> built three times, in Java, Python and TypeScript, against one contract.**

```console
$ curl -s -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' \
    -d '{"url":"https://www.aicancode.org/projects","alias":"projects"}'
{"code":"projects","shortUrl":"http://localhost:8080/projects", ...}
$ curl -sI localhost:8080/projects | head -1
HTTP/1.1 302 Found
$ curl -s localhost:8080/api/v1/links/projects/insights
{"code":"projects","provider":"template","fallback":false,"summary":"/projects got 1 clicks in the last 7 days ...", ...}
```

**Problem → solution.** Long links are unreadable and tell you nothing about who used them. SnapLink
issues short codes or custom aliases, redirects in a few milliseconds from a Redis cache, keeps per-day
click counts in PostgreSQL without storing personal data, and summarises traffic with an LLM, or a
deterministic template when no model is available. It runs on a laptop with one command.

**Architecture.** One stateless API container per track, PostgreSQL (source of truth), and Redis
(optional read-through cache): see the diagrams in [`spec/architecture.md`](spec/architecture.md).
Three decisions worth discussing:
1. Random 7-character base62 codes with retry on collision instead of sequential IDs. Not enumerable, no
   coordination needed ([ADR-0001](spec/adr/0001-short-code-generation.md)).
2. `302` instead of `301` redirects, so browsers don't cache the redirect and every click is counted
   ([ADR-0002](spec/adr/0002-302-redirects.md)). Redis absorbs the extra load as an *optional*
   read-through cache: every Redis error degrades to PostgreSQL, and the TTL never outlives a link's expiry.
3. Click counting as one atomic upsert per redirect into a per-day table: simple and privacy-friendly
   ([ADR-0003](spec/adr/0003-click-counting.md)).

**Quality evidence (v1.1.0, measured on the maintainers' Linux build box with Docker).**

| | Java 21 / Spring Boot | Python / FastAPI | TypeScript / Fastify |
|---|---|---|---|
| Tests (unit + integration) | 75 | 88 | 85 |
| Line coverage (gate 85 %) | 91.2 % | 96.8 % | 95.9 % |
| Docker smoke test (`scripts/smoke-test.sh`) | 27/27 | 27/27 | 27/27 |
| Avg redirect, warm cache (50 requests, client side) | 6.7 ms | 3.8 ms | 3.4 ms |

Every phase tag (`phase-0-end` … `phase-8-end`) builds and passes the tests of all three tracks
(`scripts/verify-tags.sh`). CI runs lint, unit and integration tests against service containers, a
coverage gate, and a Docker smoke test per track. Release tags publish images to GHCR.

**Operations.** `/health` reports database and cache separately (a Redis outage is *degraded*, not
*down*). `/metrics` exposes Prometheus counters, and every log line is JSON with the request's
`X-Request-ID`. A **game day** that stopped Redis found that the Python track slowed to ~16 s per
redirect because DNS lookups are not covered by socket timeouts. A circuit breaker fixed it in
v1.1.0: [post-mortem](docs/postmortem-2026-10-02-redis-down.md), [runbook](docs/runbook.md).

**How AI was used.** Assistants drafted Dockerfiles, workflow skeletons, test scaffolding and
first versions of docs. They were confidently wrong about version-specific details, e.g. deprecated
framework options and a deploy-hook parameter that had to be checked against the provider's docs. Every
suggestion was verified by tests, `actionlint`, the smoke test or official documentation, and logged in
[`AI_LOG.md`](AI_LOG.md). See also [`docs/retrospective.md`](docs/retrospective.md).

**What I'd do next.** Rate limiting on link creation (it is unauthenticated); a degraded-mode latency
budget enforced by a game day in CI; OpenTelemetry tracing; a small dashboard UI; a k6 load test to
check NFR-1 (p95 < 50 ms) under load.

---

SnapLink is the guided pilot project of **[AiCanCode](https://www.aicancode.org)**. Over 9 phases
(about 28 hours) you build a URL shortener like a professional team would: requirements, design, a
test-first implementation, CI/CD, containers, observability and a retrospective. AI assistants help
you at every step, and you **verify** everything they produce.

**What you build:** a REST API that shortens URLs, redirects (`302`), counts clicks, expires links, caches
hot links in Redis and generates an **"AI insights"** summary of a link's traffic. Insights work fully
offline through a template fallback; a local Ollama model or a free Gemini/Groq key are optional.

**Pick one track:** the spec, database, API contract and smoke test are shared. Only the code differs.

| Track | Stack | Folder |
|---|---|---|
| Java | Java 21 · Spring Boot · JDBC · JUnit 5 + Mockito · JaCoCo | `tracks/java` |
| Python | Python 3.11+ · FastAPI · psycopg 3 · pytest · coverage | `tracks/python` |
| TypeScript | Node 20+ · Fastify · `pg` · Vitest · ESLint | `tracks/typescript` |

Course repository: **<https://github.com/AICanCode-org/snaplink>** (`main` = starter, `solution` = reference).

Everything runs **on your laptop** with Docker Compose (app + PostgreSQL + Redis). Deploying to the
cloud is an **optional** extra in Phase 6 and needs no credit card.

---

## Repository layout
```text
.
├── README.md                 ← you are here
├── AI_LOG.md                 ← your log of AI prompts, outputs and how you verified them
├── docs/                     ← the course: phase-0 … phase-8 + GitHub Actions explained
├── spec/                     ← shared spec: requirements, architecture, ADRs, openapi.yaml, db/
├── tracks/{java,python,typescript}/   ← code + tests (same Makefile targets in each)
├── scripts/                  ← doctor.sh, check-all.sh, verify-tags.sh (+ smoke-test.sh from Phase 6)
├── cms/                      ← machine-readable export of the course for the AiCanCode site
├── docker-compose.yml        ← PostgreSQL + Redis (+ the app per track from Phase 6, optional Ollama)
└── .github/                  ← CI workflows, issue/PR templates, Dependabot
```

## Prerequisites (per operating system)
You need **Git, Docker with Compose v2, `make`, `curl`** plus the toolchain of **one** track.
Run `scripts/doctor.sh` at any time to see what is missing.

| | Windows 10/11 | macOS | Linux (Ubuntu/Debian) |
|---|---|---|---|
| Shell | **WSL2** with Ubuntu: `wsl --install` in an admin PowerShell, reboot | Terminal (zsh) | any |
| Docker | Docker Desktop, *Use the WSL 2 based engine* + *WSL integration* for Ubuntu enabled | Docker Desktop (or Colima) | Docker Engine + `docker-compose-plugin`; `sudo usermod -aG docker $USER`, log out/in |
| Git, make, curl | inside WSL: `sudo apt install git make curl` | `xcode-select --install` | `sudo apt install git make curl` |
| Java track | inside WSL: SDKMAN → `sdk install java 21-tem && sdk install maven` | SDKMAN or `brew install openjdk@21 maven` | SDKMAN |
| Python track | inside WSL: `sudo apt install python3 python3-venv` | `brew install python@3.12` | `sudo apt install python3 python3-venv` |
| TypeScript track | inside WSL: nvm → `nvm install 22` | nvm or `brew install node@22` | nvm |

**Windows, important:** clone and work **inside the WSL file system** (`~/code/snaplink`), not under
`/mnt/c/...`. It is many times faster and avoids line-ending and file-watching problems. VS Code: install
the *WSL* extension and run `code .` from the WSL terminal. Git Bash works for most things, but WSL2 is the
supported path.

Hardware: 8 GB RAM is enough. The optional Ollama model (`llama3.2:1b`) needs about 2 GB more disk/RAM.

## Quick start (5 minutes)
```bash
git clone https://github.com/<you>/snaplink.git && cd snaplink   # your fork of AICanCode-org/snaplink (docs/phase-0-setup.md)
scripts/doctor.sh                        # check your tools
cp .env.example .env                     # optional: change ports / AI provider
docker compose up -d postgres redis      # infrastructure
cd tracks/python                         # or tracks/java, tracks/typescript
make install                             # dependencies
make lint test                           # 1 passing test, the rest are "pending" until Phase 3
make run                                 # http://localhost:8080
curl -s localhost:8080/health            # {"status":"UP","checks":{"database":"UP","cache":"UP"}}
```

Every track offers the same commands:

| Command | What it does |
|---|---|
| `make install` | install dependencies (Python: creates `.venv`) |
| `make lint` | linter + formatter check (Checkstyle / ruff / ESLint + tsc) |
| `make test` | unit tests (no database needed) |
| `make test-integration` | integration tests against the `snaplink_test` database + Redis |
| `make coverage` | unit + integration with an 85 % line-coverage gate |
| `make run` | start the API on port 8080 |
| `make build` | build the Docker image (from Phase 6) |

From Phase 6 on, the app runs in Docker too: `docker compose --profile python up --build`
(or `--profile java` / `--profile typescript`), then `scripts/smoke-test.sh http://localhost:8080`.

## How the phases work
| Phase | Topic | You end at tag |
|---|---|---|
| 0 | [Setup and orientation](docs/phase-0-setup.md) | `phase-0-end` |
| 1 | [Requirements and user stories](docs/phase-1-requirements.md) | `phase-1-end` |
| 2 | [Design: architecture, DB schema, API contract](docs/phase-2-design.md) | `phase-2-end` |
| 3 | [Implementation in five milestones](docs/phase-3-implementation.md) | `phase-3-end` |
| 4 | [Testing: integration, failure tests, coverage gate](docs/phase-4-testing.md) | `phase-4-end` |
| 5 | [CI with GitHub Actions](docs/phase-5-ci.md) | `phase-5-end` |
| 6 | [Containerise, deliver, optional deploy](docs/phase-6-deploy.md) | `phase-6-end` = **`v1.0.0`** |
| 7 | [Observability and maintenance](docs/phase-7-observability.md) | `phase-7-end` = **`v1.1.0`** |
| 8 | [Retrospective and portfolio](docs/phase-8-retrospective.md) | `phase-8-end` |

Each phase page has the same 8 blocks: *Why it matters · Objectives · Step-by-step instructions ·
AI-assist prompts · Deliverables · Self-check quiz · What you learned · Catch-up git commands*.

### Branches and tags
- **`main`**: the **starter**. Tracks have the structure, the full (pending) test suite and `/health`;
  the business logic is `TODO`.
- **`solution`**: the reference implementation, one or more commits per phase, with an annotated tag at
  the end of every phase: `phase-0-end` … `phase-8-end`, plus releases `v1.0.0` and `v1.1.0`.
  Every tag builds and passes the tests of all three tracks.

**Stuck or behind?** Jump to the end state of the previous phase and continue from there:
```bash
git fetch upstream --tags
git switch -c my-phase-4 phase-3-end     # start Phase 4 from the reference end of Phase 3
```
**Compare your work with the reference:** `git diff phase-3-end -- tracks/python`.
**Peek at a single file:** `git show phase-3-end:tracks/python/app/service.py`.
Try first, then compare. You learn far more that way.

### Pending tests
The complete test suite already exists on `main` but is switched off in one list per track
(`Pending.java`, `tests/conftest.py`, `test/pending.ts`). In Phase 3 you remove one entry at a time,
watch the tests fail, and make them pass. Skipped tests are reported as skipped, never as passed.

## Working with AI
Use any assistant you like. Each phase contains copy-paste prompts and a **"Verify the output by"**
checklist. Record what you asked and how you checked it in [`AI_LOG.md`](AI_LOG.md). Never paste
secrets, `.env` files or personal data into a prompt.

## More
- [docs/README.md](docs/README.md): index of all docs
- [docs/github-actions-explained.md](docs/github-actions-explained.md): every CI/CD concept, step by step
- [spec/README.md](spec/README.md): the shared specification
- [CONTRIBUTING.md](CONTRIBUTING.md) · [CHANGELOG.md](CHANGELOG.md) · [LICENSE](LICENSE) (MIT)

Questions or problems: open an issue using the templates, or contact us via <https://www.aicancode.org/contact>.
