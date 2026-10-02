"""Hand-rolled Prometheus counters (text exposition format 0.0.4).

No client library on purpose: students see that /metrics is just text. The metric names are
identical in all three tracks (see spec/openapi.yaml, GET /metrics).
"""

from __future__ import annotations

import threading

COUNTERS: dict[str, str] = {
    "snaplink_links_created_total": "Short links created.",
    "snaplink_redirects_total": "Successful redirects (302).",
    "snaplink_cache_hits_total": "Redirect lookups served from the cache.",
    "snaplink_cache_misses_total": "Redirect lookups that went to the database.",
    "snaplink_insights_fallback_total": "Insight requests that fell back to the template generator.",
}


class Metrics:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._values = dict.fromkeys(COUNTERS, 0)

    def inc(self, name: str, amount: int = 1) -> None:
        with self._lock:
            self._values[name] += amount

    def value(self, name: str) -> int:
        return self._values[name]

    def render(self) -> str:
        lines: list[str] = []
        for name, help_text in COUNTERS.items():
            lines += [f"# HELP {name} {help_text}", f"# TYPE {name} counter", f"{name} {self._values[name]}"]
        return "\n".join(lines) + "\n"
