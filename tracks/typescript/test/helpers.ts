import type { Clock } from "../src/service.js";

/** A clock you can move. Tests should never depend on the real time. */
export class FakeClock {
  now = new Date("2026-10-02T09:30:00.000Z");
  readonly fn: Clock = () => this.now;
  advance({ days = 0, seconds = 0 }: { days?: number; seconds?: number }) {
    this.now = new Date(this.now.getTime() + days * 86_400_000 + seconds * 1000);
  }
}

export const silentLog = { warn: () => {}, error: () => {} };
