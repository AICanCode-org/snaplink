import { expect, it } from "vitest";
import { ApiError } from "../../src/errors.js";
import { validateAlias, validateDays, validateExpiresInDays, validateUrl } from "../../src/validation.js";
import { pendingDescribe } from "../pending.js";

const describe = pendingDescribe("validation");

const rejects = (fn: () => unknown) => expect(fn).toThrow(ApiError);

describe("validateUrl", () => {
  it.each(["https://www.aicancode.org", "http://example.com/a?b=c#d", "HTTPS://EXAMPLE.COM"])(
    "accepts %s",
    (u) => {
      expect(validateUrl(u)).toBe(u);
    },
  );
  it.each([
    null,
    "",
    "   ",
    "ftp://example.com",
    "javascript:alert(1)",
    "https://",
    "example.com",
    "https://exa mple.com",
    123,
    "https://a.com/" + "x".repeat(2048),
  ])("rejects %s", (u) => rejects(() => validateUrl(u)));
});

describe("validateAlias", () => {
  it.each(["abc", "my-link_2", "A".repeat(32)])("accepts %s", (a) => expect(validateAlias(a)).toBe(a));
  it.each(["ab", "A".repeat(33), "has space", "emoji😀", "api", "HEALTH", "Metrics", 42])("rejects %s", (a) =>
    rejects(() => validateAlias(a)),
  );
  it("is optional", () => expect(validateAlias(undefined)).toBeNull());
});

describe("validateExpiresInDays", () => {
  it.each([1, 365])("accepts %s", (v) => expect(validateExpiresInDays(v)).toBe(v));
  it("is optional", () => expect(validateExpiresInDays(undefined)).toBeNull());
  it.each([0, 366, "5", 2.5, true])("rejects %s", (v) => rejects(() => validateExpiresInDays(v)));
});

describe("validateDays", () => {
  it("defaults to 7 and parses strings", () => {
    expect(validateDays(undefined)).toBe(7);
    expect(validateDays("30")).toBe(30);
  });
  it.each(["0", "91", "abc"])("rejects %s", (v) => rejects(() => validateDays(v)));
});
