// Business logic. Framework-free so it can be unit-tested with in-memory adapters.
import { generateCode } from "./codes.js";
import type { LinkCache } from "./cache.js";
import type { CachedLink, DailyClicks, Link } from "./models.js";
import type { LinkRepository } from "./repository.js";

export const MAX_CACHE_TTL_SECONDS = 3600;
export const MAX_CODE_ATTEMPTS = 5;

export type Clock = () => Date;
type Logger = { warn: (msg: string) => void; error: (msg: string) => void };

export class LinkService {
  constructor(
    readonly repo: LinkRepository,
    readonly cache: LinkCache,
    readonly clock: Clock = () => new Date(),
    private readonly codeGenerator: () => string = generateCode,
    private readonly log: Logger = console,
  ) {}

  // ---- create (M2) ---------------------------------------------------
  async create(url: unknown, alias?: unknown, expiresInDays?: unknown): Promise<Link> {
    // TODO(phase-3 M2): validate the three inputs, compute expiresAt from this.clock(),
    // insert with the alias (DuplicateCodeError -> aliasTaken) or with a random code, retrying
    // up to MAX_CODE_ATTEMPTS times on DuplicateCodeError (birthday problem, ADR-0001).
    throw new Error("TODO(phase-3 M2): LinkService.create");
  }

  // ---- read / delete (M2) -----------------------------------------------
  async get(code: string): Promise<Link> {
    // TODO(phase-3 M2): repo.findByCode, throw notFound(code) when missing.
    throw new Error("TODO(phase-3 M2): LinkService.get");
  }

  async delete(code: string): Promise<void> {
    // TODO(phase-3 M2): delete from the repo (404 if missing) AND evict it from the cache.
    throw new Error("TODO(phase-3 M2): LinkService.delete");
  }

  // ---- redirect hot path (M2 + M3) ----------------------------------------
  async resolve(code: string): Promise<string> {
    // TODO(phase-3 M2/M3): cache-aside lookup (cache -> repo -> cache.put with cacheTtl), 404 if
    // unknown, 410 if expired (also evict it), then record a click. A failing recordClick must be
    // logged with this.log.error and must NOT break the redirect. Return the long URL.
    throw new Error("TODO(phase-3 M2): LinkService.resolve");
  }

  /** A cache entry must never outlive the link's expiry. */
  static cacheTtl(link: CachedLink, now: Date): number {
    // TODO(phase-3 M3): MAX_CACHE_TTL_SECONDS, but never longer than the seconds left until expiry.
    throw new Error("TODO(phase-3 M3): LinkService.cacheTtl");
  }

  // ---- analytics (M4) ------------------------------------------------------
  async stats(code: string, days?: unknown): Promise<{ link: Link; daily: DailyClicks[] }> {
    // TODO(phase-3 M4): validateDays, get the link, return { link, daily: await this.dailySeries(link, window) }.
    throw new Error("TODO(phase-3 M4): LinkService.stats");
  }

  async dailySeries(link: Link, days: number): Promise<DailyClicks[]> {
    // TODO(phase-3 M4): one entry per UTC day for the last `days` days (today included), oldest first,
    // zero-filled (repo.dailyClicks only returns days that had clicks). Hint: isoDay() in models.ts.
    throw new Error("TODO(phase-3 M4): LinkService.dailySeries");
  }
}
