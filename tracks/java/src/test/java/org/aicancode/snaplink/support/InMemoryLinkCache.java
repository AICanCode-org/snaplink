package org.aicancode.snaplink.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aicancode.snaplink.cache.LinkCache;
import org.aicancode.snaplink.link.CachedLink;

/** Test double. TTL is ignored on purpose: tests control time through the clock. */
public class InMemoryLinkCache implements LinkCache {

    public final Map<String, CachedLink> store = new HashMap<>();

    @Override
    public Optional<CachedLink> get(String code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public void put(String code, CachedLink link, long ttlSeconds) {
        store.put(code, link);
    }

    @Override
    public void delete(String code) {
        store.remove(code);
    }

    @Override
    public String status() {
        return "UP";
    }
}
