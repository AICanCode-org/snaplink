package org.aicancode.snaplink.insights;

import java.util.List;
import org.aicancode.snaplink.link.DailyClicks;

/** What an insight generator may look at: oldest-first, zero-filled, WINDOW_DAYS long. */
public record InsightInput(String code, long allTimeClicks, List<DailyClicks> daily) {

    public static final int WINDOW_DAYS = 7;
}
