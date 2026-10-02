// Short-code generation: random Base62 from a cryptographically secure source.

export const ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
export const CODE_LENGTH = 7;

export function generateCode(length = CODE_LENGTH): string {
  // TODO(phase-3 M1): return `length` characters picked from ALPHABET.
  // Use node:crypto's randomInt (cryptographically secure), NOT Math.random. Why? See ADR-0001.
  throw new Error("TODO(phase-3 M1): generateCode");
}
