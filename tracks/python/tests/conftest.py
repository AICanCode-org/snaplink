from datetime import UTC, datetime

import pytest

# ---------------------------------------------------------------------------
# PENDING tests describe behaviour you have not built yet, so they are skipped
# (reported as "s" by pytest). When you start a milestone, delete its line here,
# watch the tests fail (red), implement until they pass (green). See docs/phase-3.
# ---------------------------------------------------------------------------
PENDING: dict[str, str] = {
    "test_codes.py": "Phase 3 M1 - short codes",
    "test_validation.py": "Phase 3 M1 - validation",
    "test_service.py": "Phase 3 M2-M4 - LinkService",
    "test_api.py": "Phase 3 M2-M4 - HTTP API",
    "test_insights.py": "Phase 3 M5 - insights",
    "test_api_integration.py": "Phase 4 - integration tests",
}


def pytest_collection_modifyitems(config, items):
    for item in items:
        reason = PENDING.get(item.path.name)
        if reason:
            item.add_marker(pytest.mark.skip(reason=f"PENDING ({reason}) - see tests/conftest.py"))


class FakeClock:
    """A clock you can move. Tests should never depend on the real time."""

    def __init__(self, start: datetime | None = None):
        self.now = start or datetime(2026, 10, 2, 9, 30, tzinfo=UTC)

    def __call__(self) -> datetime:
        return self.now

    def advance(self, **kwargs):
        from datetime import timedelta

        self.now = self.now + timedelta(**kwargs)


@pytest.fixture
def clock() -> FakeClock:
    return FakeClock()
