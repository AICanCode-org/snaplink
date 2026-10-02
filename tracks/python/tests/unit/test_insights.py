import json
from datetime import date, timedelta

import httpx
import pytest

from app.insights import InsightInput, InsightService, LlmInsightGenerator, TemplateInsightGenerator
from app.models import DailyClicks


def week(clicks: list[int]) -> list[DailyClicks]:
    start = date(2026, 9, 26)
    return [DailyClicks(start + timedelta(days=i), c) for i, c in enumerate(clicks)]


def test_template_no_clicks():
    text = TemplateInsightGenerator().generate(InsightInput("abc1234", 5, week([0] * 7)))
    assert text == ("/abc1234 had no clicks in the last 7 days (all-time: 5). Share it where your audience hangs out!")


def test_template_trend_up_and_busiest_day():
    text = TemplateInsightGenerator().generate(InsightInput("abc1234", 20, week([0, 1, 0, 2, 3, 3, 1])))
    assert text == (
        "/abc1234 got 10 clicks in the last 7 days (all-time: 20). Busiest day: 2026-09-30 "
        "with 3 clicks. Trend: up compared with earlier in the week."
    )


def test_template_singular_and_steady():
    text = TemplateInsightGenerator().generate(InsightInput("x1y", 1, week([0, 0, 0, 1, 0, 0, 0])))
    assert "got 1 click in" in text and "with 1 click." in text and "Trend: steady" in text


def test_template_trend_down():
    text = TemplateInsightGenerator().generate(InsightInput("x1y", 9, week([5, 2, 1, 0, 0, 1, 0])))
    assert "Busiest day: 2026-09-26 with 5 clicks" in text and "Trend: down" in text


class Boom:
    def generate(self, data):
        raise TimeoutError("LLM took too long")


class Fixed:
    def generate(self, data):
        return "Great week!"


def test_service_uses_template_by_default():
    result = InsightService(TemplateInsightGenerator()).summarise(InsightInput("c0de", 0, week([0] * 7)))
    assert (result.provider, result.fallback) == ("template", False)


def test_service_uses_llm_when_configured():
    result = InsightService(TemplateInsightGenerator(), Fixed()).summarise(InsightInput("c0de", 0, week([0] * 7)))
    assert (result.provider, result.fallback, result.summary) == ("llm", False, "Great week!")


def test_service_falls_back_when_llm_fails():
    result = InsightService(TemplateInsightGenerator(), Boom()).summarise(InsightInput("c0de", 0, week([0] * 7)))
    assert (result.provider, result.fallback) == ("template", True)
    assert result.summary.startswith("/c0de had no clicks")


def test_llm_generator_calls_openai_compatible_endpoint():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["auth"] = request.headers.get("authorization")
        seen["body"] = request.read().decode()
        return httpx.Response(200, json={"choices": [{"message": {"content": "  Clicks are rising.  "}}]})

    gen = LlmInsightGenerator(
        "http://llm.local/v1", "test-model", "secret-key", 1000, transport=httpx.MockTransport(handler)
    )
    assert gen.generate(InsightInput("c0de", 3, week([0, 0, 0, 1, 1, 1, 0]))) == "Clicks are rising."
    assert seen["path"] == "/v1/chat/completions"
    assert seen["auth"] == "Bearer secret-key"
    body = json.loads(seen["body"])
    assert body["model"] == "test-model" and "c0de" in body["messages"][1]["content"]


def test_llm_generator_raises_on_http_error():
    gen = LlmInsightGenerator(
        "http://llm.local/v1", "m", None, 1000, transport=httpx.MockTransport(lambda r: httpx.Response(500))
    )
    with pytest.raises(httpx.HTTPStatusError):
        gen.generate(InsightInput("c0de", 0, week([0] * 7)))
