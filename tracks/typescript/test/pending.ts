import { describe } from "vitest";

// ---------------------------------------------------------------------------
// PENDING topics describe behaviour you have not built yet, so their tests are
// skipped (vitest reports them as "skipped"). When you start a milestone, delete
// its line here, watch the tests fail (red), implement until they pass (green).
// See docs/phase-3.
// ---------------------------------------------------------------------------
export const PENDING = new Set<string>([
  "codes", // Phase 3 M1 - short codes
  "validation", // Phase 3 M1 - validation
  "service", // Phase 3 M2-M4 - LinkService
  "api", // Phase 3 M2-M4 - HTTP API
  "insights", // Phase 3 M5 - insights
  "integration", // Phase 4 - integration tests
]);

/** `const describe = pendingDescribe("codes")` - a describe that is skipped while the topic is pending. */
export const pendingDescribe = (topic: string) => describe.skipIf(PENDING.has(topic));
