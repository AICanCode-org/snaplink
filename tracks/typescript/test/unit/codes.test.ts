import { expect, it } from "vitest";
import { ALPHABET, CODE_LENGTH, generateCode } from "../../src/codes.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("codes");

describe("generateCode", () => {
  it("has the expected length and alphabet", () => {
    const code = generateCode();
    expect(code).toHaveLength(CODE_LENGTH);
    expect(CODE_LENGTH).toBe(7);
    for (const ch of code) expect(ALPHABET).toContain(ch);
  });

  it("uses a Base62 alphabet", () => {
    expect(new Set(ALPHABET).size).toBe(62);
  });

  it("is random enough", () => {
    const codes = new Set(Array.from({ length: 2000 }, () => generateCode()));
    expect(codes.size).toBe(2000);
  });
});
