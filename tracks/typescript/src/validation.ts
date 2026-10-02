// Input validation rules shared by the contract (spec/openapi.yaml).
// Every function throws validationError("...") from ./errors.js (HTTP 400) for bad input.
// The exact rules are in spec/requirements.md (FR-1..FR-3) and test/unit/validation.test.ts.

export const MAX_URL_LENGTH = 2048;
export const ALIAS_PATTERN = /^[A-Za-z0-9_-]{3,32}$/;
export const RESERVED_ALIASES = new Set(["api", "health", "metrics", "docs", "admin", "static"]);

export function validateUrl(url: unknown): string {
  // TODO(phase-3 M1): non-empty string, <= MAX_URL_LENGTH, no whitespace,
  // protocol http: or https: only (new URL(...)), must have a hostname. Return the url unchanged.
  throw new Error("TODO(phase-3 M1): validateUrl");
}

export function validateAlias(alias: unknown): string | null {
  // TODO(phase-3 M1): undefined/null -> null; must match ALIAS_PATTERN; reject RESERVED_ALIASES (case-insensitive).
  throw new Error("TODO(phase-3 M1): validateAlias");
}

export function validateExpiresInDays(value: unknown): number | null {
  // TODO(phase-3 M1): undefined/null -> null; an integer number between 1 and 365.
  throw new Error("TODO(phase-3 M1): validateExpiresInDays");
}

export function validateDays(value: unknown): number {
  // TODO(phase-3 M4): undefined -> 7; otherwise digits only (it arrives as a query string), 1..90.
  throw new Error("TODO(phase-3 M4): validateDays");
}
