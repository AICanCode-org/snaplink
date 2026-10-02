---
phase: 1
slug: phase-1-requirements
title: Requirements and user stories
duration: 2 h
start_tag: phase-0-end
end_tag: phase-1-end
---
# Phase 1 · Requirements and user stories

## 1. Why it matters
Most failed projects fail before the first line of code: nobody wrote down what "done" means. A short,
testable requirements document lets you say *no* to scope creep, gives every test a reason to exist,
and is the first thing an interviewer asks about ("what were the constraints?"). AI assistants are
great at drafting requirements, and just as good at inventing requirements nobody asked for. Your
job is to keep the ones you can test.

## 2. Objectives
- Turn a one-paragraph brief into numbered **functional** (FR) and **measurable non-functional** (NFR) requirements.
- Write 6–8 **user stories** with Given/When/Then acceptance criteria and create them as GitHub Issues.
- Write an explicit **out-of-scope** list.
- Do a **back-of-the-envelope estimate** (traffic, storage, collision probability) and use it to justify the design.

## 3. Step-by-step instructions

### Step 1.1 · Read the brief and the contract
> *Build SnapLink: a self-hostable URL shortener. Users create short links (random or custom alias,
> optional expiry), visitors are redirected, owners see daily click counts and a short weekly insight.
> It must run on a laptop for free, keep working when the cache is down, and store no personal data.*

Open `spec/openapi.yaml`. The HTTP contract is **given** so the three tracks stay compatible; your
requirements must be consistent with it (status codes, limits, error format).

### Step 1.2 · Functional requirements
In `spec/requirements.md` write FR-1 … FR-n as "The system shall …" statements. Each needs a number
(1–365 days, 2048 characters, 3–32 alias characters) or a status code, so a test can check it.
Bad: *"Links should be short."* Good: *"Codes are 7 Base62 characters."*

### Step 1.3 · Non-functional requirements
Cover performance, availability, security, privacy, cost and portability. Make each one measurable:
*"p95 redirect latency < 50 ms with a warm cache"*, *"redirects keep working when Redis is down"*,
*"no IP addresses are stored"*.

### Step 1.4 · User stories → GitHub Issues
For each story use the **User story** issue template (`.github/ISSUE_TEMPLATE/user_story.yml`). It asks
for the story, the acceptance criteria and the Phase 3 milestone. Label them `user-story`. With the
GitHub CLI:
```bash
gh issue create --template user_story.yml      # or use the web UI: Issues → New issue
```

### Step 1.5 · Out of scope + estimates
List what v1 will **not** do (accounts, UI dashboard, QR codes, rate limiting…). Then estimate:
links/day, read:write ratio, requests/second at peak (× 10 of average), storage per year, and the
chance that a random 7-character code collides. Write one sentence on what the numbers mean for the design.

### Step 1.6 · Commit
```bash
git switch -c docs/phase-1-requirements
git add spec/requirements.md AI_LOG.md
git commit -m "docs(spec): requirements, user stories and estimates"
git push -u origin docs/phase-1-requirements     # open a PR, merge when CI is green
```
The track code does not change in this phase, so `make lint test` behaves exactly as in Phase 0.

## 4. AI-assist prompts

### Prompt 1: Draft requirements from the brief
```text
Act as a senior engineer. From this brief, write numbered functional requirements (FR-n, "The system
shall ...") and measurable non-functional requirements (NFR-n) for a URL shortener. Every requirement
must be testable: include numbers, limits or HTTP status codes. Stay consistent with this OpenAPI
contract: <paste the paths + schemas of spec/openapi.yaml>. Brief: <paste>. Do NOT add features that
are not in the brief; list anything you were tempted to add under "Out of scope".
```
**Verify the output by:** checking every FR against `spec/openapi.yaml` (same limits, same status codes),
deleting anything you cannot write a test for, and moving invented features (accounts, QR codes,
analytics by country) to *Out of scope*.

### Prompt 2: Acceptance criteria review
```text
Here are my user stories with acceptance criteria: <paste>. For each story, list (a) criteria that are
ambiguous or not testable, (b) a missing edge case, (c) the HTTP request + expected response that
would prove the criterion. Answer as a table.
```
**Verify the output by:** turning at least two of the suggested request/response pairs into `curl`
commands you will run in Phase 3, and rejecting edge cases that contradict the contract.

### Prompt 3: Check my estimate
```text
I estimate 1,000 new links/day and a 100:1 read:write ratio. Check my arithmetic for average and peak
requests per second, yearly storage at 0.5 KB per row, and the probability that a new random
7-character Base62 code collides when 1 million codes exist. Show each calculation step.
```
**Verify the output by:** redoing the arithmetic yourself (62⁷ ≈ 3.52 × 10¹²; 100,000 / 86,400 ≈ 1.16 req/s).
LLMs make arithmetic slips; a calculator does not.

## 5. Deliverables
- [ ] `spec/requirements.md` with FRs, measurable NFRs, ≥ 6 user stories with acceptance criteria, out of scope, estimates.
- [ ] One GitHub Issue per user story, labelled `user-story`, each assigned to a Phase 3 milestone.
- [ ] An `AI_LOG.md` entry that records at least one AI suggestion you **rejected** and why.
- [ ] Compare with the reference: `git diff phase-0-end phase-1-end -- spec/requirements.md`.

## 6. Self-check quiz

### Q1. Which requirement is testable?
- [ ] "Redirects should be fast."
- [ ] "The system should be secure."
- [x] "p95 server-side redirect latency is below 50 ms with a warm cache."
- [ ] "Users should love the aliases."
> **Answer:** Only the third one has a measurable threshold and a condition. The others need numbers before they can be tested.

### Q2. Why does SnapLink use `302` instead of `301` (and why is that a requirement, not just a detail)?
- [ ] `302` is faster
- [x] Browsers cache `301` permanently, which would break expiry, deletion and click counting
- [ ] `301` is deprecated
- [ ] Search engines require `302`
> **Answer:** FR-3, FR-5 and FR-6 only work if every click reaches the server. That constraint leads to ADR-0002 in Phase 2.

### Q3. With 1 million existing random 7-character Base62 codes, the chance that a new code collides is about…
- [ ] 1 in 62
- [ ] 1 in 1,000
- [x] 3 in 10 million
- [ ] Zero, codes are unique by construction
> **Answer:** 10⁶ / 62⁷ ≈ 10⁶ / 3.5 × 10¹² ≈ 2.8 × 10⁻⁷. That is rare, but not zero, so the database must still enforce uniqueness and the app retries.

### Q4. What belongs in "Out of scope"?
- [ ] Requirements you could not finish
- [x] Features you deliberately will not build in this version, so reviewers know it was a decision
- [ ] Bugs
- [ ] Non-functional requirements
> **Answer:** Writing the list down stops scope creep and shows judgement. "No accounts in v1" is a decision, not an omission.

### Q5. "Redirects keep working when Redis is down" is an example of…
- [ ] A functional requirement
- [x] An availability NFR that will become an integration test
- [ ] A user story
- [ ] An implementation detail you should not write down
> **Answer:** It is about *how well* the system behaves under failure. Phase 4 proves it with a test that points the cache at a dead port.

## 7. What you learned
- How to turn a fuzzy brief into numbered, testable FRs and measurable NFRs.
- User stories with Given/When/Then criteria, tracked as GitHub Issues.
- Why an explicit out-of-scope list is a professional signal.
- Back-of-the-envelope estimation and how it justifies (or rules out) complexity.
- Using AI to draft, and your own judgement and arithmetic to verify.

## 8. Catch-up git commands
```bash
git fetch upstream --tags
git switch -c my-phase-2 phase-1-end           # continue from the reference requirements
git show phase-1-end:spec/requirements.md | less
git diff phase-0-end phase-1-end --stat
```
