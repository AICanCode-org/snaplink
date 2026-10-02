// Input validation rules shared by the contract (spec/openapi.yaml).
import { validationError } from "./errors.js";

export const MAX_URL_LENGTH = 2048;
const ALIAS_PATTERN = /^[A-Za-z0-9_-]{3,32}$/;
export const RESERVED_ALIASES = new Set(["api", "health", "metrics", "docs", "admin", "static"]);

export function validateUrl(url: unknown): string {
  if (typeof url !== "string" || url.trim() === "") throw validationError("url is required");
  if (url.length > MAX_URL_LENGTH) throw validationError(`url must be at most ${MAX_URL_LENGTH} characters`);
  if (/\s/.test(url)) throw validationError("url must not contain whitespace");
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    throw validationError("url must start with http:// or https://");
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw validationError("url must start with http:// or https://");
  }
  if (!parsed.hostname) throw validationError("url must include a host");
  return url;
}

export function validateAlias(alias: unknown): string | null {
  if (alias === undefined || alias === null) return null;
  if (typeof alias !== "string" || !ALIAS_PATTERN.test(alias)) {
    throw validationError("alias must be 3-32 characters: letters, digits, '-' or '_'");
  }
  if (RESERVED_ALIASES.has(alias.toLowerCase())) throw validationError(`alias '${alias}' is reserved`);
  return alias;
}

export function validateExpiresInDays(value: unknown): number | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "number" || !Number.isInteger(value) || value < 1 || value > 365) {
    throw validationError("expiresInDays must be an integer between 1 and 365");
  }
  return value;
}

export function validateDays(value: unknown): number {
  if (value === undefined || value === null) return 7;
  const days = Number(value);
  if (!/^\d+$/.test(String(value)) || days < 1 || days > 90) {
    throw validationError("days must be an integer between 1 and 90");
  }
  return days;
}
