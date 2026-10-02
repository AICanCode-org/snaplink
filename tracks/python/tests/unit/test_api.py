"""API-level tests with in-memory adapters (no Docker needed).

These mirror the shared contract in spec/openapi.yaml; the same scenarios exist in
the Java and TypeScript tracks and in scripts/smoke-test.sh.
"""

import pytest
from fastapi.testclient import TestClient

from app.cache import InMemoryCache
from app.config import Settings
from app.main import create_app
from app.repository import InMemoryLinkRepository


@pytest.fixture
def client(clock):
    settings = Settings.from_env()
    app = create_app(settings, repo=InMemoryLinkRepository(), cache=InMemoryCache(), clock=clock)
    with TestClient(app, follow_redirects=False) as c:
        yield c


def test_create_and_get_link(client):
    r = client.post("/api/v1/links", json={"url": "https://www.aicancode.org", "alias": "acc", "expiresInDays": 7})
    assert r.status_code == 201
    assert r.headers["location"] == "/api/v1/links/acc"
    body = r.json()
    assert body["code"] == "acc" and body["custom"] is True and body["clickCount"] == 0
    assert body["shortUrl"].endswith("/acc")
    assert body["createdAt"] == "2026-10-02T09:30:00.000Z"
    assert body["expiresAt"] == "2026-10-09T09:30:00.000Z"
    assert client.get("/api/v1/links/acc").json()["url"] == "https://www.aicancode.org"


def test_validation_errors_are_problem_json(client):
    r = client.post("/api/v1/links", json={"url": "ftp://nope"})
    assert r.status_code == 400
    assert r.headers["content-type"].startswith("application/problem+json")
    assert r.json()["type"] == "https://snaplink.dev/problems/validation-error"


def test_non_object_body_is_400(client):
    headers = {"content-type": "application/json"}
    assert client.post("/api/v1/links", content="not json", headers=headers).status_code == 400
    assert client.post("/api/v1/links", json=["https://a.com"]).status_code == 400


def test_alias_conflict_is_409(client):
    client.post("/api/v1/links", json={"url": "https://a.com", "alias": "dup"})
    r = client.post("/api/v1/links", json={"url": "https://b.com", "alias": "dup"})
    assert r.status_code == 409 and r.json()["status"] == 409


def test_redirect_counts_clicks(client):
    code = client.post("/api/v1/links", json={"url": "https://www.aicancode.org"}).json()["code"]
    r = client.get(f"/{code}")
    assert r.status_code == 302
    assert r.headers["location"] == "https://www.aicancode.org"
    assert r.headers["cache-control"] == "no-store"
    client.get(f"/{code}")
    assert client.get(f"/api/v1/links/{code}").json()["clickCount"] == 2


def test_unknown_code_404(client):
    r = client.get("/doesnotexist")
    assert r.status_code == 404 and r.json()["type"].endswith("/not-found")


def test_expired_redirect_410(client, clock):
    code = client.post("/api/v1/links", json={"url": "https://a.com", "expiresInDays": 1}).json()["code"]
    clock.advance(days=2)
    assert client.get(f"/{code}").status_code == 410


def test_stats_and_insights(client):
    code = client.post("/api/v1/links", json={"url": "https://a.com", "alias": "stats1"}).json()["code"]
    for _ in range(3):
        client.get(f"/{code}")
    stats = client.get(f"/api/v1/links/{code}/stats?days=3").json()
    assert stats == {
        "code": "stats1",
        "totalClicks": 3,
        "days": 3,
        "daily": [
            {"date": "2026-09-30", "clicks": 0},
            {"date": "2026-10-01", "clicks": 0},
            {"date": "2026-10-02", "clicks": 3},
        ],
    }
    assert client.get(f"/api/v1/links/{code}/stats?days=0").status_code == 400
    insight = client.get(f"/api/v1/links/{code}/insights").json()
    assert insight["provider"] == "template" and insight["fallback"] is False
    assert insight["summary"].startswith("/stats1 got 3 clicks in the last 7 days")


def test_delete(client):
    code = client.post("/api/v1/links", json={"url": "https://a.com"}).json()["code"]
    assert client.delete(f"/api/v1/links/{code}").status_code == 204
    assert client.get(f"/{code}").status_code == 404
    assert client.delete(f"/api/v1/links/{code}").status_code == 404
