"""Integration tests: real PostgreSQL + Redis (docker compose up -d postgres redis).

Run with:  make test-integration   (sets -m integration)
Uses TEST_DATABASE_URL / TEST_REDIS_URL so your dev data is never touched.
"""

import os
from dataclasses import replace

import psycopg
import pytest
import redis
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

pytestmark = pytest.mark.integration

DB_URL = os.getenv("TEST_DATABASE_URL", "postgresql://snaplink:snaplink@localhost:5432/snaplink_test")
REDIS_URL = os.getenv("TEST_REDIS_URL", "redis://localhost:6379/1")


@pytest.fixture
def client():
    settings = replace(Settings.from_env(), database_url=DB_URL, redis_url=REDIS_URL, insights_provider="template")
    with TestClient(create_app(settings), follow_redirects=False) as c:  # runs migrations on startup
        with psycopg.connect(DB_URL, autocommit=True) as conn:
            conn.execute("TRUNCATE links RESTART IDENTITY CASCADE")
        redis.Redis.from_url(REDIS_URL).flushdb()
        yield c


def test_health_reports_real_dependencies(client):
    assert client.get("/health").json() == {"status": "UP", "checks": {"database": "UP", "cache": "UP"}}


def test_migrations_are_recorded():
    with psycopg.connect(DB_URL) as conn:
        versions = [r[0] for r in conn.execute("SELECT version FROM schema_migrations ORDER BY version")]
    assert versions[:2] == [1, 2]


def test_full_flow_persists_clicks(client):
    code = client.post("/api/v1/links", json={"url": "https://www.aicancode.org"}).json()["code"]
    for _ in range(3):
        assert client.get(f"/{code}").status_code == 302
    assert client.get(f"/api/v1/links/{code}").json()["clickCount"] == 3
    stats = client.get(f"/api/v1/links/{code}/stats").json()
    assert stats["totalClicks"] == 3 and stats["daily"][-1]["clicks"] == 3
    assert client.get(f"/api/v1/links/{code}").json()["lastAccessedAt"] is not None  # V2 column
    # The redirect populated the cache
    assert redis.Redis.from_url(REDIS_URL).exists(f"snaplink:link:{code}") == 1


def test_unique_alias_enforced_by_database(client):
    assert client.post("/api/v1/links", json={"url": "https://a.com", "alias": "uniq"}).status_code == 201
    assert client.post("/api/v1/links", json={"url": "https://b.com", "alias": "uniq"}).status_code == 409


def test_expired_in_database_is_410(client):
    code = client.post("/api/v1/links", json={"url": "https://a.com", "expiresInDays": 5}).json()["code"]
    with psycopg.connect(DB_URL, autocommit=True) as conn:
        conn.execute("UPDATE links SET expires_at = now() - interval '1 minute' WHERE code = %s", (code,))
    assert client.get(f"/{code}").status_code == 410


def test_delete_cascades_analytics_and_cache(client):
    code = client.post("/api/v1/links", json={"url": "https://a.com"}).json()["code"]
    client.get(f"/{code}")
    assert client.delete(f"/api/v1/links/{code}").status_code == 204
    with psycopg.connect(DB_URL) as conn:
        assert conn.execute("SELECT count(*) FROM click_daily").fetchone()[0] == 0
    assert redis.Redis.from_url(REDIS_URL).exists(f"snaplink:link:{code}") == 0
    assert client.get(f"/{code}").status_code == 404


def test_redis_down_degrades_gracefully():
    settings = replace(Settings.from_env(), database_url=DB_URL, redis_url="redis://localhost:6390/0")
    with TestClient(create_app(settings), follow_redirects=False) as c:
        assert c.get("/health").json()["checks"]["cache"] == "DOWN"
        code = c.post("/api/v1/links", json={"url": "https://a.com"}).json()["code"]
        assert c.get(f"/{code}").status_code == 302
