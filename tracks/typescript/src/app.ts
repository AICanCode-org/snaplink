// HTTP layer (Fastify). Thin: parse input, call the service, map errors to problem+json.
import Fastify, { type FastifyInstance, type FastifyServerOptions } from "fastify";
import { ApiError, validationError } from "./errors.js";
import { WINDOW_DAYS, type InsightService } from "./insights.js";
import type { Link } from "./models.js";
import type { LinkService } from "./service.js";

const PROBLEM_JSON = "application/problem+json";

export interface AppDeps {
  service: LinkService;
  insights: InsightService;
  publicBaseUrl: string;
}

/** ISO-8601 UTC with millisecond precision and a 'Z' suffix — identical in every track. */
const iso = (d: Date | null) => (d ? d.toISOString() : null);

export function buildApp(deps: AppDeps, opts: FastifyServerOptions = {}): FastifyInstance {
  const app = Fastify(opts);
  const { service, insights, publicBaseUrl } = deps;

  const toJson = (link: Link) => ({
    code: link.code,
    shortUrl: `${publicBaseUrl}/${link.code}`,
    url: link.longUrl,
    custom: link.custom,
    createdAt: iso(link.createdAt),
    expiresAt: iso(link.expiresAt),
    clickCount: link.clickCount,
  });

  // ---- error mapping ---------------------------------------------------
  app.setErrorHandler((err, request, reply) => {
    if (err instanceof ApiError) {
      return reply.code(err.status).type(PROBLEM_JSON).send(err.toProblem());
    }
    const status = (err as { statusCode?: number }).statusCode;
    if (status && status >= 400 && status < 500) {
      // Malformed JSON, empty body, unsupported media type...
      const problem = validationError("request body must be a JSON object").toProblem();
      return reply.code(400).type(PROBLEM_JSON).send(problem);
    }
    request.log.error(err);
    const problem = new ApiError(500, "internal-error", "Internal server error").toProblem();
    return reply.code(500).type(PROBLEM_JSON).send(problem);
  });

  // ---- routes ------------------------------------------------------------
  app.get("/health", async (_req, reply) => {
    const [dbUp, cache] = await Promise.all([service.repo.ping(), service.cache.status()]);
    return reply.code(dbUp ? 200 : 503).send({
      status: dbUp ? "UP" : "DOWN",
      checks: { database: dbUp ? "UP" : "DOWN", cache },
    });
  });

  app.post("/api/v1/links", async (req, reply) => {
    const body = req.body;
    if (typeof body !== "object" || body === null || Array.isArray(body)) {
      throw validationError("request body must be a JSON object");
    }
    const { url, alias, expiresInDays } = body as Record<string, unknown>;
    const link = await service.create(url, alias, expiresInDays);
    return reply.code(201).header("location", `/api/v1/links/${link.code}`).send(toJson(link));
  });

  app.get<{ Params: { code: string } }>("/api/v1/links/:code", async (req) =>
    toJson(await service.get(req.params.code)),
  );

  app.delete<{ Params: { code: string } }>("/api/v1/links/:code", async (req, reply) => {
    await service.delete(req.params.code);
    return reply.code(204).send();
  });

  app.get<{ Params: { code: string }; Querystring: { days?: string } }>(
    "/api/v1/links/:code/stats",
    async (req) => {
      const { link, daily } = await service.stats(req.params.code, req.query.days);
      return {
        code: link.code,
        totalClicks: link.clickCount,
        days: daily.length,
        daily: daily.map((d) => ({ date: d.day, clicks: d.clicks })),
      };
    },
  );

  app.get<{ Params: { code: string } }>("/api/v1/links/:code/insights", async (req) => {
    const link = await service.get(req.params.code);
    const daily = await service.dailySeries(link, WINDOW_DAYS);
    const result = await insights.summarise({ code: link.code, allTimeClicks: link.clickCount, daily });
    return { code: link.code, ...result, generatedAt: iso(service.clock()) };
  });

  app.get<{ Params: { code: string } }>("/:code", async (req, reply) => {
    const target = await service.resolve(req.params.code);
    return reply.code(302).header("location", target).header("cache-control", "no-store").send();
  });

  return app;
}
