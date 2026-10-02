# AI_LOG — how I used AI on this project

Employers and reviewers increasingly ask *how* you used AI, not *whether*. This log is your evidence
that you stayed in control. Add one entry every time an AI assistant (ChatGPT, Gemini, Copilot,
Cursor, Claude, a local Ollama model, …) produced something you kept, changed or rejected.

**Rules**
1. Never paste secrets, `.env` contents, API keys or other people's personal data into a prompt.
2. Every AI output is a *draft*. Record how you **verified** it (test, docs, running it, a second source).
3. Be honest about rejections — they are the most valuable entries.

## Entry template (copy for each entry)

### YYYY-MM-DD · Phase N · <short title>
* **Tool / model:** e.g. Gemini 2.0 Flash (free tier), Copilot in VS Code, Ollama llama3.2:1b
* **Prompt (summary or paste):**
  > …
* **What it produced:** one or two lines, or a link to the commit
* **What I kept / changed / rejected — and why:**
* **How I verified it:** e.g. `make test` (44 passed), compared with the OpenAPI spec, read the official docs at …
* **Time saved / lost (honest guess):**
* **What I learned:**

---

## My entries

<!-- Start below this line. Phase 0 asks you to write your first entry. -->

> The reference solution was written by the AiCanCode maintainers working with an AI coding agent.
> These entries record checks that really happened while building it. Your entries will be about *your* work.

### 2026-10-02 · Phase 0 · Toolchain check on the build machine
* **Tool / model:** AI coding agent (chat + shell)
* **Prompt (summary):**
  > Here is the output of `scripts/doctor.sh`. Everything is green except "docker daemon not reachable". What does that mean and how do I fix it safely?
* **What it produced:** the explanation that the Docker socket belongs to the `docker` group, with two
  options: add the user to the group (`sudo usermod -aG docker $USER`, then log out and in) or prefix
  Docker commands with `sudo`.
* **What I kept / changed / rejected and why:** on a shared build machine I kept `sudo docker …` rather
  than changing group membership. On a personal laptop the group is the normal fix.
* **How I verified it:** `sudo docker compose up -d postgres redis` → both `healthy`; `make run` in the
  Python starter, then `curl localhost:8080/health` → `{"status":"UP","checks":{"database":"UP","cache":"UP"}}`;
  `make lint test` in each track (1 passed, the rest skipped as pending).
* **Time saved / lost (honest guess):** saved ~10 min.
* **What I learned:** the `docker` group is effectively root access, so it is worth knowing what you are granting.

### 2026-10-02 · Phase 3 · Reviewing the expiry tests, not just the code
* **Tool / model:** AI coding agent
* **Prompt (summary):**
  > Review the expiry tests for the redirect endpoint. Do they prove that an expired link is never counted as a click?
* **What it produced:** the observation that the tests asserted `410 Gone` for an expired link but never
  checked the click counter, so an implementation that counted the click *and then* returned `410`
  would still pass.
* **What I kept / changed / rejected and why:** kept it. Every track's expiry test now also asserts that
  only the successful, pre-expiry redirect was counted (`clickCount == 1`).
* **How I verified it:** `make coverage` in all three tracks still passes with the stronger assertion
  (Java 70, Python 77, TypeScript 80 tests at v1.0).
* **Time saved / lost (honest guess):** 10 minutes for a test that now guards FR-5.
* **What I learned:** ask the assistant to attack your tests, not only your code. A green test can still prove too little.

### 2026-10-02 · Phase 6 · Deploy workflow and the Render deploy hook
* **Tool / model:** AI coding agent
* **Prompt (summary):**
  > Write `deploy.yml`: on `v*.*.*` push images to GHCR with GITHUB_TOKEN, then optionally trigger a Render deploy of that exact image, skipped when the secret is missing.
* **What it produced:** the GHCR job (`packages: write`, lower-cased owner, metadata-action tags) and a
  Render job calling the deploy hook.
* **What I kept / changed / rejected and why:** kept both, after checking the hook call against Render's
  documentation: a `POST` to the hook URL with a URL-encoded `imgURL` query parameter. The secret goes
  through `env:` because secrets cannot be used in `if:` conditions. We also chose not to present Fly.io
  as a free option, because it has no general free tier for new accounts.
* **How I verified it:** Render's deploy-hook docs; `actionlint` with shellcheck (it flagged an unused loop
  variable, SC2034, fixed with `for _ in …`); local `docker compose --profile <track> up --build` + smoke
  test 23/23 for every track. Not verified: a real GHCR push or Render deploy (no GitHub run yet).
* **Time saved / lost (honest guess):** saved ~1 h.
* **What I learned:** for anything that talks to a third-party API, the provider's docs are the only source of truth.
