// Read-through cache for the redirect hot path. Redis is optional: failures degrade to the DB.
import { Redis } from "ioredis";
import type { CachedLink } from "./models.js";

export const KEY_PREFIX = "snaplink:link:";
export type CacheStatus = "UP" | "DOWN" | "DISABLED";

export interface LinkCache {
  get(code: string): Promise<CachedLink | null>;
  put(code: string, link: CachedLink, ttlSeconds: number): Promise<void>;
  delete(code: string): Promise<void>;
  status(): Promise<CacheStatus>;
  close?(): Promise<void>;
}

export class NoopCache implements LinkCache {
  async get() {
    return null;
  }
  async put() {}
  async delete() {}
  async status(): Promise<CacheStatus> {
    return "DISABLED";
  }
}

/** Test double. TTL is ignored on purpose: tests control time through the service clock. */
export class InMemoryCache implements LinkCache {
  readonly store = new Map<string, CachedLink>();
  async get(code: string) {
    return this.store.get(code) ?? null;
  }
  async put(code: string, link: CachedLink) {
    this.store.set(code, link);
  }
  async delete(code: string) {
    this.store.delete(code);
  }
  async status(): Promise<CacheStatus> {
    return "UP";
  }
}

type Logger = { warn: (msg: string) => void };

export class RedisLinkCache implements LinkCache {
  private readonly redis: Redis;
  private lastWarn = 0;

  constructor(
    url: string,
    private readonly log: Logger = console,
  ) {
    this.redis = new Redis(url, {
      connectTimeout: 500,
      commandTimeout: 500,
      maxRetriesPerRequest: 1,
      enableOfflineQueue: false, // fail fast when Redis is down instead of queueing
      retryStrategy: (times) => Math.min(times * 200, 2000),
    });
    this.redis.on("error", (err) => this.warn(`redis error: ${err.message}`));
  }

  private warn(msg: string) {
    const now = Date.now();
    if (now - this.lastWarn > 10_000) {
      this.lastWarn = now;
      this.log.warn(msg);
    }
  }

  async get(code: string): Promise<CachedLink | null> {
    // TODO(phase-3 M3): GET KEY_PREFIX + code and JSON-decode {id, url, expiresAt} into a CachedLink.
    // Any error -> this.warn(...) and return null (the database is the source of truth).
    throw new Error("TODO(phase-3 M3): RedisLinkCache.get");
  }

  async put(code: string, link: CachedLink, ttlSeconds: number): Promise<void> {
    // TODO(phase-3 M3): SET KEY_PREFIX + code to that JSON with "EX", ttlSeconds; swallow + warn on errors.
    throw new Error("TODO(phase-3 M3): RedisLinkCache.put");
  }

  async delete(code: string): Promise<void> {
    // TODO(phase-3 M3): DEL KEY_PREFIX + code; swallow + warn on errors.
    throw new Error("TODO(phase-3 M3): RedisLinkCache.delete");
  }

  async status(): Promise<CacheStatus> {
    try {
      return (await this.redis.ping()) === "PONG" ? "UP" : "DOWN";
    } catch {
      return "DOWN";
    }
  }

  async close() {
    this.redis.disconnect();
  }
}
