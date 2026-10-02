// API-level tests with in-memory adapters (no Docker needed).
// They mirror spec/openapi.yaml; the same scenarios exist in the Java and Python tracks.
import type { FastifyInstance } from "fastify";
import { afterEach, beforeEach, expect, it } from "vitest";
import { buildApp } from "../../src/app.js";
import { InMemoryCache } from "../../src/cache.js";
import { InsightService, TemplateInsightGenerator } from "../../src/insights.js";
import { InMemoryLinkRepository } from "../../src/repository.js";
import { LinkService } from "../../src/service.js";
import { FakeClock, silentLog } from "../helpers.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("api");

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

const create = (payload: unknown) =>
  app.inject({ method: "POST", url: "/api/v1/links", payload: payload as object });

describe("SnapLink API", () => {
  it("creates and reads a link", async () => {
    const r = await create({ url: "https://www.aicancode.org", alias: "acc", expiresInDays: 7 });
    expect(r.statusCode).toBe(201);
    expect(r.headers.location).toBe("/api/v1/links/acc");
    const body = r.json();
    expect(body).toMatchObject({
      code: "acc",
      custom: true,
      clickCount: 0,
      shortUrl: "http://localhost:8080/acc",
    });
    expect(body.createdAt).toBe("2026-10-02T09:30:00.000Z");
    expect(body.expiresAt).toBe("2026-10-09T09:30:00.000Z");
    expect((await app.inject("/api/v1/links/acc")).json().url).toBe("https://www.aicancode.org");
  });

  it("returns problem+json for validation errors", async () => {
    const r = await create({ url: "ftp://nope" });
    expect(r.statusCode).toBe(400);
    expect(r.headers["content-type"]).toMatch(/^application\/problem\+json/);
    expect(r.json().type).toBe("https://snaplink.dev/problems/validation-error");
  });

  it("rejects non-object bodies with 400", async () => {
    const bad = await app.inject({
      method: "POST",
      url: "/api/v1/links",
      payload: "not json",
      headers: { "content-type": "application/json" },
    });
    expect(bad.statusCode).toBe(400);
    expect((await create(["https://a.com"])).statusCode).toBe(400);
  });

  it("returns 409 for alias conflicts", async () => {
    await create({ url: "https://a.com", alias: "dup" });
    const r = await create({ url: "https://b.com", alias: "dup" });
    expect(r.statusCode).toBe(409);
    expect(r.json().status).toBe(409);
  });

  it("redirects and counts clicks", async () => {
    const code = (await create({ url: "https://www.aicancode.org" })).json().code;
    const r = await app.inject(`/${code}`);
    expect(r.statusCode).toBe(302);
    expect(r.headers.location).toBe("https://www.aicancode.org");
    expect(r.headers["cache-control"]).toBe("no-store");
    await app.inject(`/${code}`);
    expect((await app.inject(`/api/v1/links/${code}`)).json().clickCount).toBe(2);
  });

  it("returns 404 for unknown codes", async () => {
    const r = await app.inject("/doesnotexist");
    expect(r.statusCode).toBe(404);
    expect(r.json().type).toMatch(/\/not-found$/);
  });

  it("returns 410 for expired links", async () => {
    const code = (await create({ url: "https://a.com", expiresInDays: 1 })).json().code;
    clock.advance({ days: 2 });
    expect((await app.inject(`/${code}`)).statusCode).toBe(410);
  });

  it("serves stats and insights", async () => {
    const code = (await create({ url: "https://a.com", alias: "stats1" })).json().code;
    for (let i = 0; i < 3; i++) await app.inject(`/${code}`);
    expect((await app.inject(`/api/v1/links/${code}/stats?days=3`)).json()).toEqual({
      code: "stats1",
      totalClicks: 3,
      days: 3,
      daily: [
        { date: "2026-09-30", clicks: 0 },
        { date: "2026-10-01", clicks: 0 },
        { date: "2026-10-02", clicks: 3 },
      ],
    });
    expect((await app.inject(`/api/v1/links/${code}/stats?days=0`)).statusCode).toBe(400);
    const insight = (await app.inject(`/api/v1/links/${code}/insights`)).json();
    expect(insight.provider).toBe("template");
    expect(insight.fallback).toBe(false);
    expect(insight.summary.startsWith("/stats1 got 3 clicks in the last 7 days")).toBe(true);
  });

  it("deletes links", async () => {
    const code = (await create({ url: "https://a.com" })).json().code;
    expect((await app.inject({ method: "DELETE", url: `/api/v1/links/${code}` })).statusCode).toBe(204);
    expect((await app.inject(`/${code}`)).statusCode).toBe(404);
    expect((await app.inject({ method: "DELETE", url: `/api/v1/links/${code}` })).statusCode).toBe(404);
  });
});
