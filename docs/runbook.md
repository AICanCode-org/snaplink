# SnapLink runbook

Operational how-tos for SnapLink v1.x. Keep it short, keep it true: if a step here is wrong, fix it in
the same PR that taught you so.

| | |
|---|---|
| Service | SnapLink URL shortener (one track deployed: `DEPLOY_TRACK`, default `python`) |
| Images | `ghcr.io/aicancode-org/snaplink-{java,python,typescript}:<version>` (`latest` = newest release) |
| Health | `GET /health` → `200 {"status":"UP","checks":{"database":"UP","cache":"UP"}}`. `cache: DOWN` is degraded, not down. |
| Dependencies | PostgreSQL (required), Redis (optional cache), LLM endpoint (optional; template fallback) |
| Configuration | environment variables only: see `.env.example` |

## 1. Start and stop
**Laptop (Docker Compose):**
```bash
docker compose --profile python up -d --build --wait     # or --profile java / typescript
scripts/smoke-test.sh http://localhost:8080              # expect "... 0 failed"
docker compose --profile python logs -f app-python       # follow logs
docker compose --profile python down                     # stop (keeps data)
docker compose --profile python down -v                  # stop AND delete the database volume
```
**Without Docker for the app:** `docker compose up -d postgres redis`, then `cd tracks/<track> && make run`.

**Render (optional):** Dashboard → service → **Manual Deploy** / **Suspend** / **Resume**. A free
instance sleeps when idle; the first request can take up to a minute.

## 2. Check that it works
1. `curl -s $URL/health`. `database` must be `UP`.
2. `scripts/smoke-test.sh $URL`. The smoke test creates and deletes its own links and is safe to run against production.
3. From v1.1: `curl -s $URL/metrics | grep _total`.

## 3. Release
1. Make sure `solution`/`main` CI is green (`ci-ok`).
2. `CHANGELOG.md`: move *Unreleased* under `## [x.y.z] - YYYY-MM-DD`; bump the track versions.
3. `git tag -a vX.Y.Z -m "SnapLink vX.Y.Z" && git push origin vX.Y.Z`.
4. Watch **Actions → Deploy**: three `GHCR · <track>` jobs, then `Render (optional)`.

## 4. Roll back
The database schema only ever changes in a backward-compatible way (additive migrations; see the
Phase 7 migration rule), so the previous image runs fine against the newer schema.

**Render:** call the deploy hook with the previous image:
```bash
IMG="ghcr.io/aicancode-org/snaplink-python:1.0.0"
curl -fsS -X POST "$RENDER_DEPLOY_HOOK_URL&imgURL=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$IMG")"
```
Or Dashboard → **Events** → pick the previous deploy → **Rollback**. Then run the smoke test.

**Laptop:** `git switch --detach v1.0.0 && docker compose --profile python up -d --build --wait`.

Never "roll back" by deleting a git tag or a GHCR version. Releases are immutable; fix forward with a new patch version.

## 5. Rotate a secret
| Secret | Where it lives | Rotate |
|---|---|---|
| `RENDER_DEPLOY_HOOK_URL` | GitHub environment `production` | Render → Settings → Deploy Hook → **Regenerate**, then `gh secret set RENDER_DEPLOY_HOOK_URL --env production` |
| `DATABASE_URL` password | Render env vars | Neon → Roles → reset password → update `DATABASE_URL` on Render → redeploy → smoke test |
| `REDIS_URL` | Render env vars | Upstash → reset credentials → update `REDIS_URL` → redeploy. Until then, `cache: DOWN` but redirects work. |
| `LLM_API_KEY` | Render env vars | revoke in the provider console, create a new one, update, redeploy. Until then, insights use the template (`fallback: true`). |

If a secret leaked (committed, pasted into a prompt, printed in a log): **rotate first**, then clean up.
Removing it from git history does not un-leak it.

## 6. Common incidents
| Symptom | Likely cause | Action |
|---|---|---|
| `/health` → `503`, `database: DOWN` | Postgres unreachable / wrong `DATABASE_URL` | Check the DB provider status and the connection string (`sslmode=require` on Neon); app logs show the driver error. |
| `/health` `200` but `cache: DOWN` | Redis down or `REDIS_URL` wrong | Degraded only: redirects hit Postgres. Fix Redis; the cache refills by itself. |
| Insights always `fallback: true` | LLM unreachable, slow (> `LLM_TIMEOUT_MS`) or key invalid | Expected without an LLM. Otherwise check `LLM_BASE_URL`, `LLM_MODEL` (model names change) and the key. |
| `409` on create | alias already taken | Expected behaviour. |
| Container restarts at boot | migration failed | Logs show the SQL error. Fix the migration in a new `V<n>__*.sql`; never edit an applied one. |
| First request after idle takes ~1 min | Render free tier cold start | Expected. Upgrade or accept. |

## 7. Data
- Back up: `docker compose exec -T postgres pg_dump -U snaplink snaplink > backup.sql` (Neon: branch or point-in-time restore).
- Restore: `docker compose exec -T postgres psql -U snaplink snaplink < backup.sql`.
- Privacy: only per-day click counts are stored. No IPs, no user agents (NFR-4).
