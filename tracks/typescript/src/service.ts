// Business logic. Framework-free so it can be unit-tested with in-memory adapters.
import { generateCode } from "./codes.js";
import type { LinkCache } from "./cache.js";
import { aliasTaken, DuplicateCodeError, expired, notFound } from "./errors.js";
import { isExpired, isoDay, type CachedLink, type DailyClicks, type Link } from "./models.js";
import { Metrics } from "./metrics.js";
import type { LinkRepository } from "./repository.js";
import { validateAlias, validateDays, validateExpiresInDays, validateUrl } from "./validation.js";

export const MAX_CACHE_TTL_SECONDS = 3600;
export const MAX_CODE_ATTEMPTS = 5;
const DAY_MS = 24 * 60 * 60 * 1000;

export type Clock = () => Date;
type Logger = { warn: (msg: string) => void; error: (msg: string) => void };

export class LinkService {
  constructor(
    readonly repo: LinkRepository,
    readonly cache: LinkCache,
    readonly clock: Clock = () => new Date(),
    private readonly codeGenerator: () => string = generateCode,
    private readonly log: Logger = console,
    readonly metrics: Metrics = new Metrics(),
  ) {}

  // ---- create ---------------------------------------------------------
  async create(url: unknown, alias?: unknown, expiresInDays?: unknown): Promise<Link> {
    const longUrl = validateUrl(url);
    const customAlias = validateAlias(alias);
    const days = validateExpiresInDays(expiresInDays);
    const now = this.clock();
    const expiresAt = days ? new Date(now.getTime() + days * DAY_MS) : null;

    if (customAlias) {
      let link: Link;
      try {
        link = await this.repo.insert(customAlias, longUrl, true, now, expiresAt);
      } catch (err) {
        if (err instanceof DuplicateCodeError) throw aliasTaken(customAlias);
        throw err;
      }
      this.metrics.inc("snaplink_links_created_total");
      return link;
    }
    // Random codes can collide (birthday problem); the DB unique constraint is the
    // source of truth, so we retry a few times instead of "check then insert".
    for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      let link: Link;
      try {
        link = await this.repo.insert(this.codeGenerator(), longUrl, false, now, expiresAt);
      } catch (err) {
        if (!(err instanceof DuplicateCodeError)) throw err;
        this.log.warn("short code collision, retrying");
        continue;
      }
      this.metrics.inc("snaplink_links_created_total");
      return link;
    }
    throw new Error("could not generate a unique short code");
  }

  // ---- read / delete --------------------------------------------------
  async get(code: string): Promise<Link> {
    const link = await this.repo.findByCode(code);
    if (!link) throw notFound(code);
    return link;
  }

  async delete(code: string): Promise<void> {
    if (!(await this.repo.delete(code))) throw notFound(code);
    await this.cache.delete(code);
  }

  // ---- redirect hot path ----------------------------------------------
  async resolve(code: string): Promise<string> {
    const now = this.clock();
    let cached = await this.cache.get(code);
    this.metrics.inc(cached ? "snaplink_cache_hits_total" : "snaplink_cache_misses_total");
    if (!cached) {
      const link = await this.repo.findByCode(code);
      if (!link) throw notFound(code);
      cached = { id: link.id, longUrl: link.longUrl, expiresAt: link.expiresAt };
      const ttl = LinkService.cacheTtl(cached, now);
      if (ttl > 0) await this.cache.put(code, cached, ttl);
    }
    if (isExpired(cached, now)) {
      await this.cache.delete(code);
      throw expired(code);
    }
    try {
      await this.repo.recordClick(cached.id, now);
    } catch (err) {
      // A lost click must never break a redirect.
      this.log.error(`failed to record click for ${code}: ${(err as Error).message}`);
    }
    this.metrics.inc("snaplink_redirects_total");
    return cached.longUrl;
  }

  /** A cache entry must never outlive the link's expiry. */
  static cacheTtl(link: CachedLink, now: Date): number {
    if (!link.expiresAt) return MAX_CACHE_TTL_SECONDS;
    const remaining = Math.floor((link.expiresAt.getTime() - now.getTime()) / 1000);
    return Math.min(MAX_CACHE_TTL_SECONDS, remaining);
  }

  // ---- analytics ------------------------------------------------------
  async stats(code: string, days?: unknown): Promise<{ link: Link; daily: DailyClicks[] }> {
    const window = validateDays(days);
    const link = await this.get(code);
    return { link, daily: await this.dailySeries(link, window) };
  }

  async dailySeries(link: Link, days: number): Promise<DailyClicks[]> {
    const endMs = Date.parse(isoDay(this.clock()));
    const startMs = endMs - (days - 1) * DAY_MS;
    const rows = await this.repo.dailyClicks(link.id, isoDay(new Date(startMs)), isoDay(new Date(endMs)));
    const byDay = new Map(rows.map((r) => [r.day, r.clicks]));
    const series: DailyClicks[] = [];
    for (let ms = startMs; ms <= endMs; ms += DAY_MS) {
      const day = isoDay(new Date(ms));
      series.push({ day, clicks: byDay.get(day) ?? 0 });
    }
    return series;
  }
}
