// Integration tests: real PostgreSQL + Redis (docker compose up -d postgres redis).
// Run with: npm run test:integration. Uses TEST_DATABASE_URL / TEST_REDIS_URL (never your dev DB).
import type { FastifyInstance } from "fastify";
import { Redis } from "ioredis";
import pg from "pg";
import { afterAll, beforeAll, beforeEach, expect, it } from "vitest";
import { buildApp } from "../../src/app.js";
import { RedisLinkCache } from "../../src/cache.js";
import { loadSettings } from "../../src/config.js";
import { createPool, runMigrations } from "../../src/db.js";
import { InsightService, TemplateInsightGenerator } from "../../src/insights.js";
import { PgLinkRepository } from "../../src/repository.js";
import { LinkService } from "../../src/service.js";
import { silentLog } from "../helpers.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("integration");

const DB_URL = process.env.TEST_DATABASE_URL ?? "postgresql://snaplink:snaplink@localhost:5432/snaplink_test";
const REDIS_URL = process.env.TEST_REDIS_URL ?? "redis://localhost:6379/1";

let pool: pg.Pool;
let redis: Redis;
let cache: RedisLinkCache;
let app: FastifyInstance;

const makeApp = (c: RedisLinkCache) =>
  buildApp({
    service: new LinkService(new PgLinkRepository(pool), c, undefined, undefined, silentLog),
    insights: new InsightService(new TemplateInsightGenerator()),
    publicBaseUrl: "http://localhost:8080",
  });

const create = (payload: object) => app.inject({ method: "POST", url: "/api/v1/links", payload });

describe("SnapLink with real Postgres + Redis", () => {
  beforeAll(async () => {
    pool = createPool(DB_URL);
    await runMigrations(pool, loadSettings().migrationsDir);
    redis = new Redis(REDIS_URL);
    cache = new RedisLinkCache(REDIS_URL, silentLog);
    app = makeApp(cache);
  });

  beforeEach(async () => {
    await pool.query("TRUNCATE links RESTART IDENTITY CASCADE");
    await redis.flushdb();
  });

  afterAll(async () => {
    await app.close();
    await cache.close();
    redis.disconnect();
    await pool.end();
  });

  it("reports healthy dependencies", async () => {
    expect((await app.inject("/health")).json()).toEqual({
      status: "UP",
      checks: { database: "UP", cache: "UP" },
    });
  });

  it("records applied migrations", async () => {
    const { rows } = await pool.query("SELECT version FROM schema_migrations ORDER BY version");
    expect(rows.slice(0, 2).map((r) => r.version)).toEqual([1, 2]);
  });

  it("persists clicks across the full flow", async () => {
    const code = (await create({ url: "https://www.aicancode.org" })).json().code;
    for (let i = 0; i < 3; i++) expect((await app.inject(`/${code}`)).statusCode).toBe(302);
    expect((await app.inject(`/api/v1/links/${code}`)).json().clickCount).toBe(3);
    expect((await app.inject(`/api/v1/links/${code}`)).json().lastAccessedAt).not.toBeNull(); // V2 column
    const stats = (await app.inject(`/api/v1/links/${code}/stats`)).json();
    expect(stats.totalClicks).toBe(3);
    expect(stats.daily[6].clicks).toBe(3);
    expect(await redis.exists(`snaplink:link:${code}`)).toBe(1);
  });

  it("enforces unique aliases in the database", async () => {
    expect((await create({ url: "https://a.com", alias: "uniq" })).statusCode).toBe(201);
    expect((await create({ url: "https://b.com", alias: "uniq" })).statusCode).toBe(409);
  });

  it("returns 410 when the database says the link expired", async () => {
    const code = (await create({ url: "https://a.com", expiresInDays: 5 })).json().code;
    await pool.query("UPDATE links SET expires_at = now() - interval '1 minute' WHERE code = $1", [code]);
    expect((await app.inject(`/${code}`)).statusCode).toBe(410);
  });

  it("cascades deletes to analytics and cache", async () => {
    const code = (await create({ url: "https://a.com" })).json().code;
    await app.inject(`/${code}`);
    expect((await app.inject({ method: "DELETE", url: `/api/v1/links/${code}` })).statusCode).toBe(204);
    expect((await pool.query("SELECT count(*)::int AS n FROM click_daily")).rows[0].n).toBe(0);
    expect(await redis.exists(`snaplink:link:${code}`)).toBe(0);
    expect((await app.inject(`/${code}`)).statusCode).toBe(404);
  });

  it("degrades gracefully when Redis is down", async () => {
    const deadCache = new RedisLinkCache("redis://localhost:6390/0", silentLog);
    const degraded = makeApp(deadCache);
    expect((await degraded.inject("/health")).json().checks.cache).toBe("DOWN");
    const code = (
      await degraded.inject({ method: "POST", url: "/api/v1/links", payload: { url: "https://a.com" } })
    ).json().code;
    expect((await degraded.inject(`/${code}`)).statusCode).toBe(302);
    await degraded.close();
    await deadCache.close();
  });
});
