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

  async get(code: string) {
    try {
      const raw = await this.redis.get(KEY_PREFIX + code);
      if (raw === null) return null;
      const data = JSON.parse(raw) as { id: number; url: string; expiresAt: string | null };
      return { id: data.id, longUrl: data.url, expiresAt: data.expiresAt ? new Date(data.expiresAt) : null };
    } catch (err) {
      this.warn(`cache get failed, falling back to database: ${(err as Error).message}`);
      return null;
    }
  }

  async put(code: string, link: CachedLink, ttlSeconds: number) {
    const payload = JSON.stringify({
      id: link.id,
      url: link.longUrl,
      expiresAt: link.expiresAt ? link.expiresAt.toISOString() : null,
    });
    try {
      await this.redis.set(KEY_PREFIX + code, payload, "EX", ttlSeconds);
    } catch (err) {
      this.warn(`cache put failed: ${(err as Error).message}`);
    }
  }

  async delete(code: string) {
    try {
      await this.redis.del(KEY_PREFIX + code);
    } catch (err) {
      this.warn(`cache delete failed: ${(err as Error).message}`);
    }
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
