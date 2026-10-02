import { beforeEach, expect, it } from "vitest";
import { InMemoryCache } from "../../src/cache.js";
import { ApiError } from "../../src/errors.js";
import { InMemoryLinkRepository } from "../../src/repository.js";
import { LinkService, MAX_CACHE_TTL_SECONDS } from "../../src/service.js";
import { FakeClock, silentLog } from "../helpers.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("service");

let repo: InMemoryLinkRepository;
let cache: InMemoryCache;
let clock: FakeClock;
let service: LinkService;

const status = async (p: Promise<unknown>) => {
  try {
    await p;
  } catch (e) {
    return (e as ApiError).status;
  }
  return "no error";
};

beforeEach(() => {
  repo = new InMemoryLinkRepository();
  cache = new InMemoryCache();
  clock = new FakeClock();
  service = new LinkService(repo, cache, clock.fn, undefined, silentLog);
});

describe("LinkService", () => {
  it("creates a random link", async () => {
    const link = await service.create("https://www.aicancode.org");
    expect(link.code).toHaveLength(7);
    expect(link.custom).toBe(false);
    expect(link.createdAt).toEqual(clock.now);
    expect(link.expiresAt).toBeNull();
  });

  it("creates with alias and expiry", async () => {
    const link = await service.create("https://www.aicancode.org", "aicancode", 30);
    expect(link.code).toBe("aicancode");
    expect(link.custom).toBe(true);
    expect(link.expiresAt?.toISOString()).toBe("2026-11-01T09:30:00.000Z");
  });

  it("rejects a duplicate alias with 409", async () => {
    await service.create("https://a.com", "taken");
    expect(await status(service.create("https://b.com", "taken"))).toBe(409);
  });

  it("retries random code collisions", async () => {
    const codes = ["SAME123", "SAME123", "OTHER45"];
    const s = new LinkService(repo, cache, clock.fn, () => codes.shift()!, silentLog);
    expect((await s.create("https://a.com")).code).toBe("SAME123");
    expect((await s.create("https://b.com")).code).toBe("OTHER45");
  });

  it("gives up after too many collisions", async () => {
    const s = new LinkService(repo, cache, clock.fn, () => "SAME123", silentLog);
    await s.create("https://a.com");
    await expect(s.create("https://b.com")).rejects.toThrow("unique short code");
  });

  it("resolve records a click and populates the cache", async () => {
    const link = await service.create("https://www.aicancode.org");
    expect(await service.resolve(link.code)).toBe("https://www.aicancode.org");
    expect(cache.store.has(link.code)).toBe(true);
    expect((await repo.findByCode(link.code))?.clickCount).toBe(1);
  });

  it("serves the second hit from cache", async () => {
    const link = await service.create("https://www.aicancode.org");
    await service.resolve(link.code);
    repo.findByCode = async () => {
      throw new Error("should have been served from cache");
    };
    expect(await service.resolve(link.code)).toBe("https://www.aicancode.org");
  });

  it("returns 404 for unknown codes", async () => {
    expect(await status(service.resolve("nope123"))).toBe(404);
  });

  it("returns 410 for expired links even when cached", async () => {
    const link = await service.create("https://a.com", undefined, 1);
    await service.resolve(link.code);
    clock.advance({ days: 1 });
    expect(await status(service.resolve(link.code))).toBe(410);
    expect(cache.store.has(link.code)).toBe(false);
    expect((await service.get(link.code)).clickCount).toBe(1); // the expired attempt was not counted
  });

  it("never lets the cache TTL outlive the expiry", () => {
    const now = clock.now;
    const soon = { id: 1, longUrl: "https://a.com", expiresAt: new Date(now.getTime() + 90_000) };
    expect(LinkService.cacheTtl(soon, now)).toBe(90);
    expect(LinkService.cacheTtl({ id: 2, longUrl: "https://b.com", expiresAt: null }, now)).toBe(
      MAX_CACHE_TTL_SECONDS,
    );
  });

  it("delete removes link and cache entry", async () => {
    const link = await service.create("https://a.com");
    await service.resolve(link.code);
    await service.delete(link.code);
    expect(cache.store.has(link.code)).toBe(false);
    expect(await status(service.get(link.code))).toBe(404);
  });

  it("delete of unknown code is 404", async () => {
    expect(await status(service.delete("missing"))).toBe(404);
  });

  it("a failing click write does not break the redirect", async () => {
    const link = await service.create("https://a.com");
    repo.recordClick = async () => {
      throw new Error("db hiccup");
    };
    expect(await service.resolve(link.code)).toBe("https://a.com");
  });

  it("stats are zero-filled, oldest first", async () => {
    const link = await service.create("https://a.com");
    await service.resolve(link.code);
    clock.advance({ days: 2 });
    await service.resolve(link.code);
    await service.resolve(link.code);
    const { link: found, daily } = await service.stats(link.code, "7");
    expect(found.clickCount).toBe(3);
    expect(daily).toHaveLength(7);
    expect(daily[6]).toEqual({ day: "2026-10-04", clicks: 2 });
    expect(daily[4]).toEqual({ day: "2026-10-02", clicks: 1 });
  });
});
