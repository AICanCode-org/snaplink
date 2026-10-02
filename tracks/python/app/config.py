"""Configuration from environment variables (12-factor). Same names in every track."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _default_migrations_dir() -> str:
    """tracks/python/app/config.py -> <repo>/spec/db/migrations (only used outside Docker)."""
    parents = Path(__file__).resolve().parents
    return str(parents[3] / "spec" / "db" / "migrations") if len(parents) > 3 else "migrations"


@dataclass(frozen=True)
class Settings:
    database_url: str
    redis_url: str | None
    public_base_url: str
    port: int
    migrations_dir: str
    insights_provider: str  # "template" | "llm"
    llm_base_url: str
    llm_api_key: str | None
    llm_model: str
    llm_timeout_ms: int

    @staticmethod
    def from_env() -> Settings:
        return Settings(
            database_url=os.getenv("DATABASE_URL", "postgresql://snaplink:snaplink@localhost:5432/snaplink"),
            redis_url=os.getenv("REDIS_URL") or None,
            public_base_url=os.getenv("PUBLIC_BASE_URL", "http://localhost:8080").rstrip("/"),
            port=int(os.getenv("PORT", "8080")),
            migrations_dir=os.getenv("MIGRATIONS_DIR") or _default_migrations_dir(),
            insights_provider=os.getenv("INSIGHTS_PROVIDER", "template").lower(),
            llm_base_url=os.getenv("LLM_BASE_URL", "http://localhost:11434/v1").rstrip("/"),
            llm_api_key=os.getenv("LLM_API_KEY") or None,
            llm_model=os.getenv("LLM_MODEL", "llama3.2:1b"),
            llm_timeout_ms=int(os.getenv("LLM_TIMEOUT_MS", "5000")),
        )
