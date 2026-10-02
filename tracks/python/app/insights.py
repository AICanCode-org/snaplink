"""'Weekly link insights': template by default, optional LLM with automatic fallback."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Protocol

import httpx

from .models import DailyClicks

log = logging.getLogger("snaplink.insights")
WINDOW_DAYS = 7


@dataclass(frozen=True)
class InsightInput:
    code: str
    all_time_clicks: int
    daily: list[DailyClicks]  # oldest first, zero-filled, WINDOW_DAYS long


class InsightGenerator(Protocol):
    def generate(self, data: InsightInput) -> str: ...


def _clicks(n: int) -> str:
    return f"{n} click" if n == 1 else f"{n} clicks"


class TemplateInsightGenerator:
    """Deterministic, offline, free. Identical wording in all three tracks."""

    def generate(self, data: InsightInput) -> str:
        total = sum(d.clicks for d in data.daily)
        if total == 0:
            return (
                f"/{data.code} had no clicks in the last {WINDOW_DAYS} days "
                f"(all-time: {data.all_time_clicks}). Share it where your audience hangs out!"
            )
        busiest = data.daily[0]
        for day in data.daily[1:]:
            if day.clicks > busiest.clicks:  # strict '>' so the earliest day wins ties
                busiest = day
        half = len(data.daily) // 2
        previous = sum(d.clicks for d in data.daily[:half])
        recent = sum(d.clicks for d in data.daily[len(data.daily) - half :])
        trend = "up" if recent > previous else "down" if recent < previous else "steady"
        return (
            f"/{data.code} got {_clicks(total)} in the last {WINDOW_DAYS} days "
            f"(all-time: {data.all_time_clicks}). Busiest day: {busiest.day.isoformat()} "
            f"with {_clicks(busiest.clicks)}. Trend: {trend} compared with earlier in the week."
        )


SYSTEM_PROMPT = (
    "You are an analytics assistant for a URL shortener. Write at most two short sentences in plain "
    "English, no markdown. Use only the numbers provided; never invent data."
)


class LlmInsightGenerator:
    """Calls any OpenAI-compatible /chat/completions endpoint (Ollama, Gemini, Groq)."""

    def __init__(
        self,
        base_url: str,
        model: str,
        api_key: str | None,
        timeout_ms: int,
        transport: httpx.BaseTransport | None = None,
    ):
        headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._client = httpx.Client(base_url=base_url, headers=headers, timeout=timeout_ms / 1000, transport=transport)
        self._model = model

    def generate(self, data: InsightInput) -> str:
        facts = {
            "code": data.code,
            "allTimeClicks": data.all_time_clicks,
            "daily": [{"date": d.day.isoformat(), "clicks": d.clicks} for d in data.daily],
        }
        response = self._client.post(
            "/chat/completions",
            json={
                "model": self._model,
                "temperature": 0.2,
                "max_tokens": 120,
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": "Summarise these click stats:\n" + json.dumps(facts)},
                ],
            },
        )
        response.raise_for_status()
        text = response.json()["choices"][0]["message"]["content"].strip()
        if not text:
            raise ValueError("empty LLM response")
        return text


@dataclass(frozen=True)
class InsightResult:
    provider: str  # "template" | "llm"
    fallback: bool
    summary: str


class InsightService:
    def __init__(self, template: InsightGenerator, llm: InsightGenerator | None = None):
        self._template = template
        self._llm = llm

    def summarise(self, data: InsightInput) -> InsightResult:
        if self._llm is not None:
            try:
                return InsightResult("llm", False, self._llm.generate(data))
            except Exception as exc:  # noqa: BLE001 - any LLM failure -> graceful fallback
                log.warning("LLM insight failed, using template: %s", exc)
                return InsightResult("template", True, self._template.generate(data))
        return InsightResult("template", False, self._template.generate(data))
