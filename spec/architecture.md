# SnapLink architecture

> **Phase 2 deliverable.** Replace the TODOs. Diagrams use [Mermaid](https://mermaid.js.org/), which
> GitHub renders natively. Reference: `git show phase-2-end:spec/architecture.md`.

## 1. Context diagram
TODO: who/what talks to SnapLink?

## 2. Container diagram
TODO: app, PostgreSQL, Redis, optional LLM. Which calls are on the hot path?

## 3. Redirect sequence (cache-aside)
TODO: sequence diagram for `GET /{code}` with cache hit, cache miss, expired, Redis down.

## 4. Data model
TODO: tables, keys, indexes, and why (link to `db/migrations/V1__init.sql`).

## 5. Key decisions
TODO: link your ADRs (`adr/0001-…`).
