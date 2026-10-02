// 'Weekly link insights': template by default, optional LLM with automatic fallback.
import type { DailyClicks } from "./models.js";

export const WINDOW_DAYS = 7;

export interface InsightInput {
  code: string;
  allTimeClicks: number;
  daily: DailyClicks[]; // oldest first, zero-filled, WINDOW_DAYS long
}

export interface InsightGenerator {
  generate(data: InsightInput): Promise<string>;
}

/** Deterministic, offline, free. Identical wording in all three tracks (spec/requirements.md FR-9). */
export class TemplateInsightGenerator implements InsightGenerator {
  async generate(data: InsightInput): Promise<string> {
    // TODO(phase-3 M5): build the exact sentence from the spec: total clicks in the window,
    // busiest day (earliest wins ties), trend = last half vs first half of the window.
    throw new Error("TODO(phase-3 M5): TemplateInsightGenerator.generate");
  }
}

export const SYSTEM_PROMPT =
  "You are an analytics assistant for a URL shortener. Write at most two short sentences in plain " +
  "English, no markdown. Use only the numbers provided; never invent data.";

/** Calls any OpenAI-compatible /chat/completions endpoint (Ollama, Gemini, Groq). */
export class LlmInsightGenerator implements InsightGenerator {
  constructor(
    private readonly baseUrl: string,
    private readonly model: string,
    private readonly apiKey: string | null,
    private readonly timeoutMs: number,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async generate(data: InsightInput): Promise<string> {
    // TODO(phase-3 M5): POST `${this.baseUrl}/chat/completions` with this.fetchImpl, a Bearer header when
    // apiKey is set, AbortSignal.timeout(this.timeoutMs), SYSTEM_PROMPT + the facts as JSON
    // (temperature 0.2, max_tokens 120). Throw on !response.ok or an empty answer.
    throw new Error("TODO(phase-3 M5): LlmInsightGenerator.generate");
  }
}

export interface InsightResult {
  provider: "template" | "llm";
  fallback: boolean;
  summary: string;
}

export class InsightService {
  constructor(
    private readonly template: InsightGenerator,
    private readonly llm: InsightGenerator | null = null,
    private readonly log: { warn: (msg: string) => void } = console,
  ) {}

  async summarise(data: InsightInput): Promise<InsightResult> {
    // TODO(phase-3 M5): no LLM -> template (fallback false). With an LLM: try it -> provider "llm";
    // on ANY error this.log.warn(...) and return the template text with fallback true.
    throw new Error("TODO(phase-3 M5): InsightService.summarise");
  }
}
