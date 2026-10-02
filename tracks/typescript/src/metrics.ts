// Hand-rolled Prometheus counters (text exposition format 0.0.4).
// No client library on purpose: /metrics is just text. Names are identical in all three tracks.

export const COUNTERS = {
  snaplink_links_created_total: "Short links created.",
  snaplink_redirects_total: "Successful redirects (302).",
  snaplink_cache_hits_total: "Redirect lookups served from the cache.",
  snaplink_cache_misses_total: "Redirect lookups that went to the database.",
  snaplink_insights_fallback_total: "Insight requests that fell back to the template generator.",
} as const;

export type CounterName = keyof typeof COUNTERS;

export class Metrics {
  private values = new Map<CounterName, number>(
    (Object.keys(COUNTERS) as CounterName[]).map((name) => [name, 0]),
  );

  inc(name: CounterName, amount = 1) {
    this.values.set(name, (this.values.get(name) ?? 0) + amount);
  }

  value(name: CounterName) {
    return this.values.get(name) ?? 0;
  }

  render(): string {
    const lines: string[] = [];
    for (const [name, help] of Object.entries(COUNTERS) as [CounterName, string][]) {
      lines.push(`# HELP ${name} ${help}`, `# TYPE ${name} counter`, `${name} ${this.value(name)}`);
    }
    return lines.join("\n") + "\n";
  }
}
