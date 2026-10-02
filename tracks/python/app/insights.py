"""'Weekly link insights': template by default, optional LLM with automatic fallback."""

from __future__ import annotations

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


class TemplateInsightGenerator:
    """Deterministic, offline, free. Identical wording in all three tracks (see spec/requirements.md FR-9)."""

    def generate(self, data: InsightInput) -> str:
        # TODO(phase-3 M5): build the exact sentence from the spec: total clicks in the window,
        # busiest day (earliest wins ties), trend = last half vs first half of the window.
        raise NotImplementedError("TODO(phase-3 M5): TemplateInsightGenerator.generate")


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
        # TODO(phase-3 M5): POST /chat/completions with SYSTEM_PROMPT + the facts as JSON
        # (model, temperature 0.2, max_tokens 120). raise_for_status(); return choices[0].message.content
        # stripped; raise ValueError on an empty answer.
        raise NotImplementedError("TODO(phase-3 M5): LlmInsightGenerator.generate")


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
        # TODO(phase-3 M5): no LLM -> InsightResult("template", False, ...). With an LLM: try it and return
        # ("llm", False, ...); on ANY exception log a warning and return ("template", True, template text).
        raise NotImplementedError("TODO(phase-3 M5): InsightService.summarise")
