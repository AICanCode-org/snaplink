"""Phase 7: request IDs, /metrics, lastAccessedAt (v1.1.0)."""

import json
import logging

import pytest
from fastapi.testclient import TestClient

from app.cache import InMemoryCache
from app.config import Settings
from app.main import create_app
from app.metrics import Metrics
from app.observability import JsonFormatter, incoming_or_new_id
from app.repository import InMemoryLinkRepository


@pytest.fixture
def client(clock):
    app = create_app(Settings.from_env(), repo=InMemoryLinkRepository(), cache=InMemoryCache(), clock=clock)
    with TestClient(app, follow_redirects=False) as c:
        yield c


def metric(client, name: str) -> int:
    for line in client.get("/metrics").text.splitlines():
        if line.startswith(name + " "):
            return int(line.split()[1])
    raise AssertionError(f"{name} not exported")


def test_request_id_is_generated_and_echoed(client):
    generated = client.get("/health").headers["x-request-id"]
    assert len(generated) == 36  # UUID
    assert client.get("/health", headers={"X-Request-ID": "abc-123"}).headers["x-request-id"] == "abc-123"


def test_unsafe_request_ids_are_replaced():
    assert incoming_or_new_id("ok.id_1-2") == "ok.id_1-2"
    assert incoming_or_new_id("bad id\nInjected: yes") != "bad id\nInjected: yes"
    assert incoming_or_new_id("x" * 65) != "x" * 65
    assert incoming_or_new_id(None)


def test_metrics_endpoint_counts_events(client):
    r = client.get("/metrics")
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/plain")
    assert "# TYPE snaplink_redirects_total counter" in r.text
    code = client.post("/api/v1/links", json={"url": "https://a.com"}).json()["code"]
    client.get(f"/{code}")  # miss -> DB, then cached
    client.get(f"/{code}")  # hit
    assert metric(client, "snaplink_links_created_total") == 1
    assert metric(client, "snaplink_redirects_total") == 2
    assert metric(client, "snaplink_cache_misses_total") == 1
    assert metric(client, "snaplink_cache_hits_total") == 1
    assert metric(client, "snaplink_insights_fallback_total") == 0


def test_last_accessed_at(client, clock):
    code = client.post("/api/v1/links", json={"url": "https://a.com"}).json()["code"]
    assert client.get(f"/api/v1/links/{code}").json()["lastAccessedAt"] is None
    clock.advance(hours=1)
    client.get(f"/{code}")
    assert client.get(f"/api/v1/links/{code}").json()["lastAccessedAt"] == "2026-10-02T10:30:00.000Z"


def test_json_formatter_emits_one_object_per_line():
    record = logging.LogRecord("snaplink.access", logging.INFO, __file__, 1, "request", None, None)
    record.fields = {"requestId": "r1", "status": 200}
    entry = json.loads(JsonFormatter().format(record))
    assert entry["msg"] == "request" and entry["requestId"] == "r1" and entry["level"] == "INFO"


def test_metrics_render_format():
    m = Metrics()
    m.inc("snaplink_redirects_total", 3)
    text = m.render()
    assert "snaplink_redirects_total 3\n" in text and text.endswith("\n")
