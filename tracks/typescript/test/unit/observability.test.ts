// Phase 7: request IDs, /metrics, lastAccessedAt (v1.1.0).
import type { FastifyInstance } from "fastify";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "../../src/app.js";
import { InMemoryCache } from "../../src/cache.js";
import { InsightService, TemplateInsightGenerator } from "../../src/insights.js";
import { Metrics } from "../../src/metrics.js";
import { incomingOrNewId } from "../../src/observability.js";
import { InMemoryLinkRepository } from "../../src/repository.js";
import { LinkService } from "../../src/service.js";
import { FakeClock, silentLog } from "../helpers.js";

let app: FastifyInstance;
let clock: FakeClock;

beforeEach(() => {
  clock = new FakeClock();
  const service = new LinkService(
    new InMemoryLinkRepository(),
    new InMemoryCache(),
    clock.fn,
    undefined,
    silentLog,
  );
  app = buildApp({
    service,
    insights: new InsightService(new TemplateInsightGenerator()),
    publicBaseUrl: "http://localhost:8080",
  });
});
afterEach(() => app.close());

const metric = async (name: string) => {
  const text = (await app.inject("/metrics")).body;
  const line = text.split("\n").find((l) => l.startsWith(`${name} `));
  if (!line) throw new Error(`${name} not exported`);
  return Number(line.split(" ")[1]);
};

const createCode = async () =>
  (await app.inject({ method: "POST", url: "/api/v1/links", payload: { url: "https://a.com" } })).json()
    .code as string;

describe("observability", () => {
  it("generates and echoes request IDs", async () => {
    const generated = (await app.inject("/health")).headers["x-request-id"] as string;
    expect(generated).toHaveLength(36);
    const echoed = await app.inject({ url: "/health", headers: { "x-request-id": "abc-123" } });
    expect(echoed.headers["x-request-id"]).toBe("abc-123");
  });

  it("replaces unsafe request IDs", () => {
    expect(incomingOrNewId("ok.id_1-2")).toBe("ok.id_1-2");
    expect(incomingOrNewId("bad id\nInjected: yes")).not.toBe("bad id\nInjected: yes");
    expect(incomingOrNewId("x".repeat(65))).not.toBe("x".repeat(65));
    expect(incomingOrNewId(undefined)).toHaveLength(36);
  });

  it("exports counters on /metrics", async () => {
    const r = await app.inject("/metrics");
    expect(r.statusCode).toBe(200);
    expect(r.headers["content-type"]).toMatch(/^text\/plain/);
    expect(r.body).toContain("# TYPE snaplink_redirects_total counter");
    const code = await createCode();
    await app.inject(`/${code}`); // miss -> DB, then cached
    await app.inject(`/${code}`); // hit
    expect(await metric("snaplink_links_created_total")).toBe(1);
    expect(await metric("snaplink_redirects_total")).toBe(2);
    expect(await metric("snaplink_cache_misses_total")).toBe(1);
    expect(await metric("snaplink_cache_hits_total")).toBe(1);
    expect(await metric("snaplink_insights_fallback_total")).toBe(0);
  });

  it("tracks lastAccessedAt", async () => {
    const code = await createCode();
    expect((await app.inject(`/api/v1/links/${code}`)).json().lastAccessedAt).toBeNull();
    clock.advance({ seconds: 3600 });
    await app.inject(`/${code}`);
    expect((await app.inject(`/api/v1/links/${code}`)).json().lastAccessedAt).toBe(
      "2026-10-02T10:30:00.000Z",
    );
  });

  it("renders the Prometheus text format", () => {
    const m = new Metrics();
    m.inc("snaplink_redirects_total", 3);
    const text = m.render();
    expect(text).toContain("snaplink_redirects_total 3\n");
    expect(text.endsWith("\n")).toBe(true);
  });
});
