package org.aicancode.snaplink.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** ISO-8601 UTC with millisecond precision and a 'Z' suffix — identical in every track. */
public final class Iso {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Iso() {
    }

    public static String format(Instant instant) {
        return instant == null ? null : FORMAT.format(instant);
    }
}
