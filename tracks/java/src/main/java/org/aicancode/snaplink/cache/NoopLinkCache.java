package org.aicancode.snaplink.cache;

import java.util.Optional;
import org.aicancode.snaplink.link.CachedLink;

/** Used when REDIS_URL is not set. */
public class NoopLinkCache implements LinkCache {

    @Override
    public Optional<CachedLink> get(String code) {
        return Optional.empty();
    }

    @Override
    public void put(String code, CachedLink link, long ttlSeconds) {
        // caching disabled
    }

    @Override
    public void delete(String code) {
        // caching disabled
    }

    @Override
    public String status() {
        return "DISABLED";
    }
}
