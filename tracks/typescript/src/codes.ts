// Short-code generation: random Base62 from a cryptographically secure source.
import { randomInt } from "node:crypto";

export const ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
export const CODE_LENGTH = 7;

export function generateCode(length = CODE_LENGTH): string {
  let code = "";
  for (let i = 0; i < length; i++) code += ALPHABET[randomInt(ALPHABET.length)];
  return code;
}
