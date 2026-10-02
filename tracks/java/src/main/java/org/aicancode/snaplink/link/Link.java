package org.aicancode.snaplink.link;

import java.time.Instant;

/** A short link as stored in the database. */
public record Link(long id, String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt,
                   long clickCount) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
