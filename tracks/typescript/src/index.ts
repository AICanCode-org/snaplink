// Process entrypoint: wire real adapters, run migrations, start listening on $PORT.
import { buildApp } from "./app.js";
import { NoopCache, RedisLinkCache, type LinkCache } from "./cache.js";
import { loadSettings } from "./config.js";
import { createPool, runMigrations } from "./db.js";
import { InsightService, LlmInsightGenerator, TemplateInsightGenerator } from "./insights.js";
import { PgLinkRepository } from "./repository.js";
import { LinkService } from "./service.js";

const settings = loadSettings();
const pool = createPool(settings.databaseUrl);
const applied = await runMigrations(pool, settings.migrationsDir);
console.log(`migrations applied this start: ${applied.length ? applied.join(",") : "none"}`);

// Cache warnings go through Fastify's JSON logger like every other line (`app` exists before Redis can fail).
const cacheLog = { warn: (msg: string) => app.log.warn(msg) };
const cache: LinkCache = settings.redisUrl
  ? new RedisLinkCache(settings.redisUrl, cacheLog)
  : new NoopCache();
const service = new LinkService(new PgLinkRepository(pool), cache);
const llm =
  settings.insightsProvider === "llm"
    ? new LlmInsightGenerator(
        settings.llmBaseUrl,
        settings.llmModel,
        settings.llmApiKey,
        settings.llmTimeoutMs,
      )
    : null;
const insights = new InsightService(new TemplateInsightGenerator(), llm);

const app = buildApp({ service, insights, publicBaseUrl: settings.publicBaseUrl }, { logger: true });

const shutdown = async () => {
  await app.close();
  await cache.close?.();
  await pool.end();
  process.exit(0);
};
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

await app.listen({ host: "0.0.0.0", port: settings.port });
