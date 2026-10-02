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
