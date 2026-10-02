package org.aicancode.snaplink.link;

import java.time.Instant;

/** A short link as stored in the database. lastAccessedAt was added in v1.1.0 (V2 migration). */
public record Link(long id, String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt,
                   long clickCount, Instant lastAccessedAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
