package org.aicancode.snaplink.link;

import java.time.LocalDate;

/** Clicks for one UTC day. */
public record DailyClicks(LocalDate day, long clicks) {
}
