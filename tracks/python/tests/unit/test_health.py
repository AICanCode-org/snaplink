"""Works from day one: the starter already serves /health (no business logic needed)."""

from fastapi.testclient import TestClient

from app.cache import InMemoryCache
from app.config import Settings
from app.main import create_app
from app.repository import InMemoryLinkRepository


def test_health():
    app = create_app(Settings.from_env(), repo=InMemoryLinkRepository(), cache=InMemoryCache())
    with TestClient(app) as client:
        r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP", "checks": {"database": "UP", "cache": "UP"}}
