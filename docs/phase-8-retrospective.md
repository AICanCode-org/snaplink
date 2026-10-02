---
phase: 8
slug: phase-8-retrospective
title: Retrospective and portfolio
duration: 1.5 h
start_tag: phase-7-end
end_tag: phase-8-end
---
# Phase 8 · Retrospective and portfolio

## 1. Why it matters
A recruiter spends about a minute on your repository. A README that tells the story (problem,
design, trade-offs, evidence, what you would do next) turns "another URL shortener" into proof that
you can design, test, ship and operate software, and that you use AI responsibly. The retrospective
is for you: it turns nine phases of experience into lessons you can explain in an interview.

## 2. Objectives
- Rewrite the README top section as a **case study** with real numbers from your project.
- Write a short **retrospective** (what went well, what didn't, what you'd change).
- Summarise your **AI_LOG** into three honest bullet points.
- Prepare a 2-minute walkthrough and answers to common interview questions about the project.

## 3. Step-by-step instructions

### Step 8.1 · Collect your evidence
```bash
git log --oneline | wc -l                       # commits
git tag -l 'v*'                                  # releases
make coverage                                    # coverage % per track
scripts/smoke-test.sh | tail -1                  # e.g. "27 passed, 0 failed"
curl -s localhost:8080/metrics | grep _total     # metrics after a demo session
```
Also grab: a screenshot of the CI run graph, the GHCR package page and (optionally) your live URL.

### Step 8.2 · README case study
At the top of `README.md`, before the setup instructions, add a section with:
1. **One-line pitch** and a demo GIF or `curl` session.
2. **Problem → solution** in three sentences.
3. **Architecture** (link the Mermaid diagram) and the **three decisions** you are proudest of, linking ADRs.
4. **Quality evidence:** tests, coverage, CI badge, smoke test, failure tests (Redis down).
5. **Operations:** health, metrics, request IDs, the post-mortem.
6. **How I used AI**, with honest examples of accepted and rejected suggestions.
7. **What I'd do next** (rate limiting, a dashboard UI, k6 load test, OpenTelemetry tracing…).

### Step 8.3 · Retrospective (`docs/retrospective.md`)
Use *Start / Stop / Continue* or *What went well / What didn't / What I'd change*. Include one
technical lesson (e.g. cache TTL must respect expiry), one process lesson (test-first with a pending
list), and one AI lesson (an assistant confidently produced a wrong answer and a test caught it).

### Step 8.4 · Practise the walkthrough
Record yourself (2 minutes): problem, architecture diagram, one interesting piece of code, the test
that proves it, the CI run, what you'd do next. Then answer out loud:
- *Why 302 and not 301?* · *What happens when Redis is down?* · *How do you avoid duplicate codes?*
- *How do you know your tests are good, not just numerous?* · *Where did AI help, and where did it mislead you?*

### Step 8.5 · Share it
Pin the repository on your GitHub profile, add the topics `url-shortener`, `<your-language>`,
`docker`, `github-actions`, and post the case study (aicancode.org *Build in public*, LinkedIn).

## 4. AI-assist prompts

### Prompt 1: Draft the case study from evidence
```text
Write a README "case study" section (max 350 words) for my URL shortener project from these facts
ONLY: <paste your evidence: test counts, coverage, ADR titles, metrics, post-mortem summary, AI_LOG
highlights>. Use short headings. Do not invent numbers, features or users. Mark anything you are
unsure about with [CHECK].
```
**Verify the output by:** checking every number and claim against your evidence list and deleting
anything you cannot show in the repository. If a claim has no link to a file, test or run, cut it.

### Prompt 2: Mock interview
```text
Act as a backend interviewer. Ask me 8 questions about this project, one at a time, from easy to
hard, based on this README and architecture: <paste>. After each answer, tell me what a strong answer
would add. Start now with question 1.
```
**Verify the output by:** checking its "strong answers" against your ADRs and the official docs. An
interviewer persona can still be wrong about details.

## 5. Deliverables
- [ ] README case study with real evidence and links.
- [ ] `docs/retrospective.md`.
- [ ] AI usage summary (three bullets) in the README, backed by `AI_LOG.md`.
- [ ] A recorded 2-minute walkthrough (optional, but recommended).

## 6. Self-check quiz

### Q1. Which README claim is strongest?
- [ ] "Highly scalable and production-ready."
- [ ] "Uses best practices."
- [x] "Redirects keep working when Redis is down, proven by an integration test (link) and a game day (post-mortem link)."
- [ ] "Built with AI."
> **Answer:** Specific, verifiable, linked. Reviewers trust evidence, not adjectives.

### Q2. What belongs in "What I'd do next"?
- [x] Concrete, prioritised improvements with a reason (e.g. rate limiting because creation is unauthenticated)
- [ ] Everything that is missing
- [ ] Apologies for unfinished work
- [ ] Nothing, it signals weakness
> **Answer:** Knowing the next steps and why shows judgement, and gives the interviewer something to discuss.

### Q3. How should you describe AI use in your portfolio?
- [ ] Hide it
- [ ] Claim the AI wrote everything
- [x] Honestly, with examples of what you verified, changed and rejected
- [ ] Only mention it if asked
> **Answer:** Responsible AI use is a skill employers look for. Your AI_LOG is the evidence.

### Q4. An interviewer asks "What happens when Redis is down?" Which answer is best?
- [ ] "Redis never goes down in production."
- [ ] "The app crashes, but it restarts automatically."
- [x] "Redirects fall back to PostgreSQL, /health shows cache DOWN with status UP, and I measured the latency impact in a game day."
- [ ] "I would add more Redis replicas."
> **Answer:** Describe the designed behaviour, how you proved it (test + game day), and what you learned
> (e.g. the DNS-timeout surprise in the reference post-mortem).

### Q5. Which retrospective item is most useful?
- [ ] "Testing was hard."
- [x] "Our Redis-down test used a closed port; next time we test the realistic failure (host gone) and assert a latency budget."
- [ ] "Everything went well."
- [ ] "The AI made mistakes."
> **Answer:** A good retrospective item names a specific cause and a concrete, testable change.

## 7. What you learned
- Turning a project into a case study backed by evidence.
- Running a retrospective and extracting transferable lessons.
- Communicating trade-offs and AI usage clearly and honestly.
- The full lifecycle: requirements → design → implementation → tests → CI/CD → operations → reflection.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git show phase-8-end:README.md | less           # reference case study
git show phase-8-end:docs/retrospective.md
git diff phase-7-end phase-8-end --stat         # docs only
```
