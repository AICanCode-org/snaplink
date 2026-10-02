package org.aicancode.snaplink.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-rolled Prometheus counters (text exposition format 0.0.4).
 * No client library on purpose: /metrics is just text. Names are identical in all three tracks.
 */
public class Metrics {

    public static final String LINKS_CREATED = "snaplink_links_created_total";
    public static final String REDIRECTS = "snaplink_redirects_total";
    public static final String CACHE_HITS = "snaplink_cache_hits_total";
    public static final String CACHE_MISSES = "snaplink_cache_misses_total";
    public static final String INSIGHTS_FALLBACK = "snaplink_insights_fallback_total";

    private static final Map<String, String> HELP = new LinkedHashMap<>();

    static {
        HELP.put(LINKS_CREATED, "Short links created.");
        HELP.put(REDIRECTS, "Successful redirects (302).");
        HELP.put(CACHE_HITS, "Redirect lookups served from the cache.");
        HELP.put(CACHE_MISSES, "Redirect lookups that went to the database.");
        HELP.put(INSIGHTS_FALLBACK, "Insight requests that fell back to the template generator.");
    }

    private final Map<String, AtomicLong> values = new LinkedHashMap<>();

    public Metrics() {
        HELP.keySet().forEach(name -> values.put(name, new AtomicLong()));
    }

    public void inc(String name) {
        inc(name, 1);
    }

    public void inc(String name, long amount) {
        AtomicLong counter = values.get(name);
        if (counter == null) {
            throw new IllegalArgumentException("unknown metric " + name);
        }
        counter.addAndGet(amount);
    }

    public long value(String name) {
        return values.get(name).get();
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        HELP.forEach((name, help) -> sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" counter\n")
                .append(name).append(' ').append(values.get(name).get()).append('\n'));
        return sb.toString();
    }
}
