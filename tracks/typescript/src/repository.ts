// Persistence. LinkRepository is the port; Postgres and in-memory are adapters.
import pg from "pg";
import { DuplicateCodeError } from "./errors.js";
import type { DailyClicks, Link } from "./models.js";

export interface LinkRepository {
  insert(
    code: string,
    longUrl: string,
    custom: boolean,
    createdAt: Date,
    expiresAt: Date | null,
  ): Promise<Link>;
  findByCode(code: string): Promise<Link | null>;
  delete(code: string): Promise<boolean>;
  recordClick(linkId: number, day: string): Promise<void>;
  dailyClicks(linkId: number, start: string, end: string): Promise<DailyClicks[]>;
  ping(): Promise<boolean>;
}

// pg returns BIGINT as string and DATE as a local-midnight Date; normalise both.
pg.types.setTypeParser(20, (v) => Number.parseInt(v, 10));
pg.types.setTypeParser(1082, (v) => v);

export const COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count";

interface LinkRow {
  id: number;
  code: string;
  long_url: string;
  is_custom: boolean;
  created_at: Date;
  expires_at: Date | null;
  click_count: number;
}

export const toLink = (r: LinkRow): Link => ({
  id: r.id,
  code: r.code,
  longUrl: r.long_url,
  custom: r.is_custom,
  createdAt: r.created_at,
  expiresAt: r.expires_at,
  clickCount: r.click_count,
});

export class PgLinkRepository implements LinkRepository {
  constructor(private readonly pool: pg.Pool) {}

  async insert(
    code: string,
    longUrl: string,
    custom: boolean,
    createdAt: Date,
    expiresAt: Date | null,
  ): Promise<Link> {
    // TODO(phase-3 M2): INSERT ... RETURNING ${COLUMNS}; map Postgres error code 23505 to DuplicateCodeError.
    // Use toLink(rows[0]) to convert the row.
    throw new Error("TODO(phase-3 M2): PgLinkRepository.insert");
  }

  async findByCode(code: string): Promise<Link | null> {
    // TODO(phase-3 M2): SELECT ${COLUMNS} FROM links WHERE code = $1
    throw new Error("TODO(phase-3 M2): PgLinkRepository.findByCode");
  }

  async delete(code: string): Promise<boolean> {
    // TODO(phase-3 M2): DELETE ... and return true if a row was deleted (click_daily cascades).
    throw new Error("TODO(phase-3 M2): PgLinkRepository.delete");
  }

  async recordClick(linkId: number, day: string): Promise<void> {
    // TODO(phase-3 M3): ONE atomic statement that bumps links.click_count and upserts
    // click_daily(link_id, day) (hint: a CTE + INSERT ... ON CONFLICT DO UPDATE).
    throw new Error("TODO(phase-3 M3): PgLinkRepository.recordClick");
  }

  async dailyClicks(linkId: number, start: string, end: string): Promise<DailyClicks[]> {
    // TODO(phase-3 M4): rows of click_daily for linkId with start <= day <= end, ordered by day.
    throw new Error("TODO(phase-3 M4): PgLinkRepository.dailyClicks");
  }

  async ping() {
    try {
      await this.pool.query("SELECT 1");
      return true;
    } catch {
      return false;
    }
  }
}

/** Test double with the same behaviour as Postgres (unique codes, cascade delete). */
export class InMemoryLinkRepository implements LinkRepository {
  private links = new Map<string, Link>();
  private clicks = new Map<string, number>(); // key: `${linkId}|${day}`
  private nextId = 1;

  async insert(code: string, longUrl: string, custom: boolean, createdAt: Date, expiresAt: Date | null) {
    if (this.links.has(code)) throw new DuplicateCodeError(code);
    const link: Link = { id: this.nextId++, code, longUrl, custom, createdAt, expiresAt, clickCount: 0 };
    this.links.set(code, link);
    return { ...link };
  }

  async findByCode(code: string) {
    const link = this.links.get(code);
    return link ? { ...link } : null;
  }

  async delete(code: string) {
    const link = this.links.get(code);
    if (!link) return false;
    this.links.delete(code);
    for (const key of [...this.clicks.keys()]) if (key.startsWith(`${link.id}|`)) this.clicks.delete(key);
    return true;
  }

  async recordClick(linkId: number, day: string) {
    for (const link of this.links.values()) {
      if (link.id === linkId) {
        link.clickCount += 1;
        const key = `${linkId}|${day}`;
        this.clicks.set(key, (this.clicks.get(key) ?? 0) + 1);
        return;
      }
    }
  }

  async dailyClicks(linkId: number, start: string, end: string) {
    const result: DailyClicks[] = [];
    for (const [key, clicks] of this.clicks) {
      const [id, day] = key.split("|") as [string, string];
      if (Number(id) === linkId && day >= start && day <= end) result.push({ day, clicks });
    }
    return result.sort((a, b) => a.day.localeCompare(b.day));
  }

  async ping() {
    return true;
  }
}
