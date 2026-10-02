package org.aicancode.snaplink.link;

import java.time.Instant;

/** The minimum the redirect path needs. Stored in Redis as JSON. */
public record CachedLink(long id, String longUrl, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
