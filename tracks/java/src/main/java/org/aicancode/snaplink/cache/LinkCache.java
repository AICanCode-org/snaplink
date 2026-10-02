package org.aicancode.snaplink.cache;

import java.util.Optional;
import org.aicancode.snaplink.link.CachedLink;

/** Read-through cache for the redirect hot path. Implementations must never throw. */
public interface LinkCache {

    Optional<CachedLink> get(String code);

    void put(String code, CachedLink link, long ttlSeconds);

    void delete(String code);

    /** "UP", "DOWN" or "DISABLED". */
    String status();
}
