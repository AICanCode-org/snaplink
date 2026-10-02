export interface Link {
  id: number;
  code: string;
  longUrl: string;
  custom: boolean;
  createdAt: Date;
  expiresAt: Date | null;
  clickCount: number;
}

/** The minimum the redirect path needs. Stored in Redis as JSON. */
export interface CachedLink {
  id: number;
  longUrl: string;
  expiresAt: Date | null;
}

/** day is an ISO date (YYYY-MM-DD) in UTC. */
export interface DailyClicks {
  day: string;
  clicks: number;
}

export const isExpired = (link: { expiresAt: Date | null }, now: Date) =>
  link.expiresAt !== null && now.getTime() >= link.expiresAt.getTime();

export const isoDay = (d: Date) => d.toISOString().slice(0, 10);
