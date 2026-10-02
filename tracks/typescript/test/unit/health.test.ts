// Works from day one: the starter already serves /health (no business logic needed).
import { expect, it } from "vitest";
import { buildApp } from "../../src/app.js";
import { InMemoryCache } from "../../src/cache.js";
import { InsightService, TemplateInsightGenerator } from "../../src/insights.js";
import { InMemoryLinkRepository } from "../../src/repository.js";
import { LinkService } from "../../src/service.js";

it("GET /health", async () => {
  const app = buildApp({
    service: new LinkService(new InMemoryLinkRepository(), new InMemoryCache()),
    insights: new InsightService(new TemplateInsightGenerator()),
    publicBaseUrl: "http://localhost:8080",
  });
  const r = await app.inject("/health");
  expect(r.statusCode).toBe(200);
  expect(r.json()).toEqual({ status: "UP", checks: { database: "UP", cache: "UP" } });
  await app.close();
});
