import { expect, it } from "vitest";
import {
  InsightService,
  LlmInsightGenerator,
  TemplateInsightGenerator,
  type InsightGenerator,
} from "../../src/insights.js";
import type { DailyClicks } from "../../src/models.js";
import { silentLog } from "../helpers.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("insights");

const week = (clicks: number[]): DailyClicks[] =>
  clicks.map((c, i) => ({ day: new Date(Date.UTC(2026, 8, 26 + i)).toISOString().slice(0, 10), clicks: c }));
const template = new TemplateInsightGenerator();

describe("TemplateInsightGenerator", () => {
  it("handles a week with no clicks", async () => {
    expect(
      await template.generate({ code: "abc1234", allTimeClicks: 5, daily: week([0, 0, 0, 0, 0, 0, 0]) }),
    ).toBe(
      "/abc1234 had no clicks in the last 7 days (all-time: 5). Share it where your audience hangs out!",
    );
  });

  it("reports busiest day and an upward trend", async () => {
    expect(
      await template.generate({ code: "abc1234", allTimeClicks: 20, daily: week([0, 1, 0, 2, 3, 3, 1]) }),
    ).toBe(
      "/abc1234 got 10 clicks in the last 7 days (all-time: 20). Busiest day: 2026-09-30 " +
        "with 3 clicks. Trend: up compared with earlier in the week.",
    );
  });

  it("uses singular wording and 'steady'", async () => {
    const text = await template.generate({
      code: "x1y",
      allTimeClicks: 1,
      daily: week([0, 0, 0, 1, 0, 0, 0]),
    });
    expect(text).toContain("got 1 click in");
    expect(text).toContain("with 1 click.");
    expect(text).toContain("Trend: steady");
  });

  it("detects a downward trend", async () => {
    const text = await template.generate({
      code: "x1y",
      allTimeClicks: 9,
      daily: week([5, 2, 1, 0, 0, 1, 0]),
    });
    expect(text).toContain("Busiest day: 2026-09-26 with 5 clicks");
    expect(text).toContain("Trend: down");
  });
});

const fixed: InsightGenerator = { generate: async () => "Great week!" };
const boom: InsightGenerator = {
  generate: async () => {
    throw new Error("LLM took too long");
  },
};
const input = { code: "c0de", allTimeClicks: 0, daily: week([0, 0, 0, 0, 0, 0, 0]) };

describe("InsightService", () => {
  it("uses the template by default", async () => {
    expect(await new InsightService(template).summarise(input)).toMatchObject({
      provider: "template",
      fallback: false,
    });
  });
  it("uses the LLM when configured", async () => {
    expect(await new InsightService(template, fixed).summarise(input)).toEqual({
      provider: "llm",
      fallback: false,
      summary: "Great week!",
    });
  });
  it("falls back to the template when the LLM fails", async () => {
    const result = await new InsightService(template, boom, silentLog).summarise(input);
    expect(result.provider).toBe("template");
    expect(result.fallback).toBe(true);
    expect(result.summary.startsWith("/c0de had no clicks")).toBe(true);
  });
});

describe("LlmInsightGenerator", () => {
  it("calls an OpenAI-compatible endpoint", async () => {
    let seen: { url: string; init: RequestInit } | undefined;
    const fakeFetch = (async (url: string, init: RequestInit) => {
      seen = { url, init };
      return new Response(JSON.stringify({ choices: [{ message: { content: "  Clicks are rising.  " } }] }));
    }) as unknown as typeof fetch;
    const gen = new LlmInsightGenerator("http://llm.local/v1", "test-model", "secret-key", 1000, fakeFetch);
    expect(await gen.generate(input)).toBe("Clicks are rising.");
    expect(seen?.url).toBe("http://llm.local/v1/chat/completions");
    expect((seen?.init.headers as Record<string, string>).Authorization).toBe("Bearer secret-key");
    const body = JSON.parse(seen?.init.body as string);
    expect(body.model).toBe("test-model");
    expect(body.messages[1].content).toContain("c0de");
  });

  it("throws on HTTP errors", async () => {
    const fakeFetch = (async () => new Response("nope", { status: 500 })) as unknown as typeof fetch;
    const gen = new LlmInsightGenerator("http://llm.local/v1", "m", null, 1000, fakeFetch);
    await expect(gen.generate(input)).rejects.toThrow("LLM HTTP 500");
  });
});
