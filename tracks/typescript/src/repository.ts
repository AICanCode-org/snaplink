// Persistence. LinkRepository is the port; Postgres and in-memory are adapters.
import pg from "pg";
import { DuplicateCodeError } from "./errors.js";
import { isoDay, type DailyClicks, type Link } from "./models.js";

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
  recordClick(linkId: number, at: Date): Promise<void>;
  dailyClicks(linkId: number, start: string, end: string): Promise<DailyClicks[]>;
  ping(): Promise<boolean>;
}

// pg returns BIGINT as string and DATE as a local-midnight Date; normalise both.
pg.types.setTypeParser(20, (v) => Number.parseInt(v, 10));
pg.types.setTypeParser(1082, (v) => v);

const COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count, last_accessed_at";

interface LinkRow {
  id: number;
  code: string;
  long_url: string;
  is_custom: boolean;
  created_at: Date;
  expires_at: Date | null;
  click_count: number;
  last_accessed_at: Date | null;
}

const toLink = (r: LinkRow): Link => ({
  id: r.id,
  code: r.code,
  longUrl: r.long_url,
  custom: r.is_custom,
  createdAt: r.created_at,
  expiresAt: r.expires_at,
  clickCount: r.click_count,
  lastAccessedAt: r.last_accessed_at,
});

export class PgLinkRepository implements LinkRepository {
  constructor(private readonly pool: pg.Pool) {}

  async insert(code: string, longUrl: string, custom: boolean, createdAt: Date, expiresAt: Date | null) {
    try {
      const { rows } = await this.pool.query<LinkRow>(
        `INSERT INTO links (code, long_url, is_custom, created_at, expires_at)
         VALUES ($1, $2, $3, $4, $5) RETURNING ${COLUMNS}`,
        [code, longUrl, custom, createdAt, expiresAt],
      );
      return toLink(rows[0]!);
    } catch (err) {
      if ((err as { code?: string }).code === "23505") throw new DuplicateCodeError(code);
      throw err;
    }
  }

  async findByCode(code: string) {
    const { rows } = await this.pool.query<LinkRow>(`SELECT ${COLUMNS} FROM links WHERE code = $1`, [code]);
    return rows[0] ? toLink(rows[0]) : null;
  }

  async delete(code: string) {
    const result = await this.pool.query("DELETE FROM links WHERE code = $1", [code]);
    return (result.rowCount ?? 0) > 0;
  }

  async recordClick(linkId: number, at: Date) {
    // One statement = one atomic unit: bump the total, remember when, upsert the daily bucket.
    await this.pool.query(
      `WITH bumped AS (
         UPDATE links SET click_count = click_count + 1, last_accessed_at = $3 WHERE id = $1 RETURNING id
       )
       INSERT INTO click_daily (link_id, day, clicks)
       SELECT id, $2::date, 1 FROM bumped
       ON CONFLICT (link_id, day) DO UPDATE SET clicks = click_daily.clicks + 1`,
      [linkId, isoDay(at), at],
    );
  }

  async dailyClicks(linkId: number, start: string, end: string) {
    const { rows } = await this.pool.query<{ day: string; clicks: number }>(
      "SELECT day, clicks FROM click_daily WHERE link_id = $1 AND day BETWEEN $2 AND $3 ORDER BY day",
      [linkId, start, end],
    );
    return rows.map((r) => ({ day: r.day, clicks: r.clicks }));
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
    const link: Link = {
      id: this.nextId++,
      code,
      longUrl,
      custom,
      createdAt,
      expiresAt,
      clickCount: 0,
      lastAccessedAt: null,
    };
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

  async recordClick(linkId: number, at: Date) {
    for (const link of this.links.values()) {
      if (link.id === linkId) {
        link.clickCount += 1;
        link.lastAccessedAt = at;
        const key = `${linkId}|${isoDay(at)}`;
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
