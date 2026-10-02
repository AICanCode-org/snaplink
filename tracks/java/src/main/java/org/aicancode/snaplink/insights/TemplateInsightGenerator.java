package org.aicancode.snaplink.insights;

import java.util.List;
import org.aicancode.snaplink.link.DailyClicks;

/** Deterministic, offline, free. Identical wording in all three tracks. */
public class TemplateInsightGenerator implements InsightGenerator {

    @Override
    public String generate(InsightInput data) {
        List<DailyClicks> daily = data.daily();
        long total = sum(daily);
        int window = InsightInput.WINDOW_DAYS;
        if (total == 0) {
            return "/" + data.code() + " had no clicks in the last " + window + " days (all-time: "
                    + data.allTimeClicks() + "). Share it where your audience hangs out!";
        }
        DailyClicks busiest = daily.get(0);
        for (DailyClicks day : daily.subList(1, daily.size())) {
            if (day.clicks() > busiest.clicks()) { // strict '>' so the earliest day wins ties
                busiest = day;
            }
        }
        int half = daily.size() / 2;
        long previous = sum(daily.subList(0, half));
        long recent = sum(daily.subList(daily.size() - half, daily.size()));
        String trend = recent > previous ? "up" : recent < previous ? "down" : "steady";
        return "/" + data.code() + " got " + clicks(total) + " in the last " + window + " days (all-time: "
                + data.allTimeClicks() + "). Busiest day: " + busiest.day() + " with " + clicks(busiest.clicks())
                + ". Trend: " + trend + " compared with earlier in the week.";
    }

    private static long sum(List<DailyClicks> days) {
        return days.stream().mapToLong(DailyClicks::clicks).sum();
    }

    private static String clicks(long n) {
        return n == 1 ? n + " click" : n + " clicks";
    }
}
