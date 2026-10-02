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

const clicks = (n: number) => (n === 1 ? `${n} click` : `${n} clicks`);
const sum = (xs: DailyClicks[]) => xs.reduce((acc, d) => acc + d.clicks, 0);

/** Deterministic, offline, free. Identical wording in all three tracks. */
export class TemplateInsightGenerator implements InsightGenerator {
  async generate(data: InsightInput): Promise<string> {
    const total = sum(data.daily);
    if (total === 0) {
      return (
        `/${data.code} had no clicks in the last ${WINDOW_DAYS} days ` +
        `(all-time: ${data.allTimeClicks}). Share it where your audience hangs out!`
      );
    }
    let busiest = data.daily[0]!;
    for (const day of data.daily.slice(1)) if (day.clicks > busiest.clicks) busiest = day; // earliest wins ties
    const half = Math.floor(data.daily.length / 2);
    const previous = sum(data.daily.slice(0, half));
    const recent = sum(data.daily.slice(data.daily.length - half));
    const trend = recent > previous ? "up" : recent < previous ? "down" : "steady";
    return (
      `/${data.code} got ${clicks(total)} in the last ${WINDOW_DAYS} days ` +
      `(all-time: ${data.allTimeClicks}). Busiest day: ${busiest.day} ` +
      `with ${clicks(busiest.clicks)}. Trend: ${trend} compared with earlier in the week.`
    );
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
    const facts = {
      code: data.code,
      allTimeClicks: data.allTimeClicks,
      daily: data.daily.map((d) => ({ date: d.day, clicks: d.clicks })),
    };
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (this.apiKey) headers.Authorization = `Bearer ${this.apiKey}`;
    const response = await this.fetchImpl(`${this.baseUrl}/chat/completions`, {
      method: "POST",
      headers,
      signal: AbortSignal.timeout(this.timeoutMs),
      body: JSON.stringify({
        model: this.model,
        temperature: 0.2,
        max_tokens: 120,
        messages: [
          { role: "system", content: SYSTEM_PROMPT },
          { role: "user", content: "Summarise these click stats:\n" + JSON.stringify(facts) },
        ],
      }),
    });
    if (!response.ok) throw new Error(`LLM HTTP ${response.status}`);
    const json = (await response.json()) as { choices?: { message?: { content?: string } }[] };
    const text = json.choices?.[0]?.message?.content?.trim();
    if (!text) throw new Error("empty LLM response");
    return text;
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
    if (this.llm) {
      try {
        return { provider: "llm", fallback: false, summary: await this.llm.generate(data) };
      } catch (err) {
        this.log.warn(`LLM insight failed, using template: ${(err as Error).message}`);
        return { provider: "template", fallback: true, summary: await this.template.generate(data) };
      }
    }
    return { provider: "template", fallback: false, summary: await this.template.generate(data) };
  }
}
