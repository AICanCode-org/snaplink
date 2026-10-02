# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-02
### Added
- Requirements (FR-1..9, NFR-1..7), architecture diagrams, ADRs 0001-0003, `V1__init.sql`, OpenAPI 1.0.0.
- Link API in all three tracks: create (random code or alias, optional expiry), get, delete, `302`
  redirect with click counting, daily stats, AI insights with offline template fallback.
- Redis read-through cache that degrades to PostgreSQL when Redis is down.
- Unit + integration tests per track, 85 % line-coverage gate.
- CI: path filters, reusable per-track workflow with service containers, Docker image smoke test, `ci-ok`.
- Dockerfiles (multi-stage, non-root, health check), Compose profiles per track, `scripts/smoke-test.sh`.
- `deploy.yml`: images to GHCR on version tags; optional Render deploy.
- `docs/runbook.md`.

## [0.1.0] - starter
### Added
- Starter: three track skeletons (Java, Python, TypeScript), shared OpenAPI contract, `/health`,
  Docker Compose with PostgreSQL + Redis, minimal CI.
