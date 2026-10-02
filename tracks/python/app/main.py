"""HTTP layer (FastAPI). Thin: parse input, call the service, map errors to problem+json."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Annotated

from fastapi import Body, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse, Response

from .cache import LinkCache, NoopCache, RedisLinkCache
from .config import Settings
from .db import create_pool, run_migrations
from .errors import ApiError, validation_error
from .insights import WINDOW_DAYS, InsightInput, InsightService, LlmInsightGenerator, TemplateInsightGenerator
from .models import Link
from .repository import LinkRepository, PostgresLinkRepository
from .service import LinkService, utc_now

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("snaplink")
PROBLEM_JSON = "application/problem+json"


def iso(dt: datetime | None) -> str | None:
    """ISO-8601 UTC with millisecond precision and a 'Z' suffix — identical in every track."""
    if dt is None:
        return None
    return dt.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def build_insight_service(settings: Settings) -> InsightService:
    template = TemplateInsightGenerator()
    if settings.insights_provider == "llm":
        llm = LlmInsightGenerator(
            settings.llm_base_url, settings.llm_model, settings.llm_api_key, settings.llm_timeout_ms
        )
        return InsightService(template, llm)
    return InsightService(template)


def create_app(
    settings: Settings | None = None,
    *,
    repo: LinkRepository | None = None,
    cache: LinkCache | None = None,
    clock=utc_now,
    insights: InsightService | None = None,
) -> FastAPI:
    settings = settings or Settings.from_env()
    state: dict = {}

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        pool = None
        link_repo = repo
        if link_repo is None:
            pool = create_pool(settings.database_url)
            applied = run_migrations(pool, settings.migrations_dir)
            log.info("migrations applied this start: %s", applied or "none")
            link_repo = PostgresLinkRepository(pool)
        link_cache = cache or (RedisLinkCache(settings.redis_url) if settings.redis_url else NoopCache())
        state["service"] = LinkService(link_repo, link_cache, clock)
        state["insights"] = insights or build_insight_service(settings)
        yield
        if pool is not None:
            pool.close()

    app = FastAPI(title="SnapLink (Python track)", version="1.0.0", lifespan=lifespan)

    def service() -> LinkService:
        return state["service"]

    def to_json(link: Link) -> dict:
        return {
            "code": link.code,
            "shortUrl": f"{settings.public_base_url}/{link.code}",
            "url": link.long_url,
            "custom": link.custom,
            "createdAt": iso(link.created_at),
            "expiresAt": iso(link.expires_at),
            "clickCount": link.click_count,
        }

    # ---- error mapping ---------------------------------------------------
    @app.exception_handler(ApiError)
    async def api_error_handler(_req: Request, exc: ApiError):
        return JSONResponse(exc.to_problem(), status_code=exc.status, media_type=PROBLEM_JSON)

    @app.exception_handler(RequestValidationError)
    async def request_validation_handler(_req: Request, _exc: RequestValidationError):
        problem = validation_error("request body must be a JSON object").to_problem()
        return JSONResponse(problem, status_code=400, media_type=PROBLEM_JSON)

    @app.exception_handler(Exception)
    async def unexpected_handler(_req: Request, exc: Exception):
        log.exception("unhandled error", exc_info=exc)
        problem = ApiError(500, "internal-error", "Internal server error").to_problem()
        return JSONResponse(problem, status_code=500, media_type=PROBLEM_JSON)

    # ---- routes ------------------------------------------------------------
    @app.get("/health")
    def health():
        svc = service()
        db_up = svc.repo.ping()
        body = {
            "status": "UP" if db_up else "DOWN",
            "checks": {"database": "UP" if db_up else "DOWN", "cache": svc.cache.status()},
        }
        return JSONResponse(body, status_code=200 if db_up else 503)

    @app.post("/api/v1/links", status_code=201)
    def create_link(payload: Annotated[dict, Body()]):
        link = service().create(payload.get("url"), payload.get("alias"), payload.get("expiresInDays"))
        return JSONResponse(to_json(link), status_code=201, headers={"Location": f"/api/v1/links/{link.code}"})

    @app.get("/api/v1/links/{code}")
    def get_link(code: str):
        return to_json(service().get(code))

    @app.delete("/api/v1/links/{code}", status_code=204)
    def delete_link(code: str):
        service().delete(code)
        return Response(status_code=204)

    @app.get("/api/v1/links/{code}/stats")
    def get_stats(code: str, days: str | None = None):
        link, daily = service().stats(code, days)
        return {
            "code": link.code,
            "totalClicks": link.click_count,
            "days": len(daily),
            "daily": [{"date": d.day.isoformat(), "clicks": d.clicks} for d in daily],
        }

    @app.get("/api/v1/links/{code}/insights")
    def get_insights(code: str):
        svc = service()
        link = svc.get(code)
        data = InsightInput(link.code, link.click_count, svc.daily_series(link, WINDOW_DAYS))
        result = state["insights"].summarise(data)
        return {
            "code": link.code,
            "provider": result.provider,
            "fallback": result.fallback,
            "summary": result.summary,
            "generatedAt": iso(svc.clock()),
        }

    @app.get("/{code}")
    def redirect(code: str):
        target = service().resolve(code)
        return RedirectResponse(target, status_code=302, headers={"Cache-Control": "no-store"})

    return app
