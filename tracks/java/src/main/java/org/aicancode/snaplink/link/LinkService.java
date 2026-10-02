package org.aicancode.snaplink.link;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.aicancode.snaplink.cache.LinkCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Business logic. Framework-free so it can be unit-tested with in-memory adapters. */
public class LinkService {

    public static final long MAX_CACHE_TTL_SECONDS = 3600;
    public static final int MAX_CODE_ATTEMPTS = 5;
    private static final Logger LOG = LoggerFactory.getLogger(LinkService.class);

    private final LinkRepository repo;
    private final LinkCache cache;
    private final Clock clock;
    private final Supplier<String> codeGenerator;

    public LinkService(LinkRepository repo, LinkCache cache, Clock clock, Supplier<String> codeGenerator) {
        this.repo = repo;
        this.cache = cache;
        this.clock = clock;
        this.codeGenerator = codeGenerator;
    }

    public LinkRepository repository() {
        return repo;
    }

    public LinkCache cache() {
        return cache;
    }

    public Instant now() {
        return clock.instant();
    }

    // ---- create (M2) -------------------------------------------------------
    public Link create(Object url, Object alias, Object expiresInDays) {
        // TODO(phase-3 M2): validate the three inputs, compute expiresAt from clock.instant(), insert with the
        // alias (DuplicateCodeException -> ApiException.aliasTaken) or with codeGenerator.get(), retrying up to
        // MAX_CODE_ATTEMPTS times on DuplicateCodeException (birthday problem, ADR-0001).
        throw new UnsupportedOperationException("TODO(phase-3 M2): LinkService.create");
    }

    // ---- read / delete (M2) ------------------------------------------------
    public Link get(String code) {
        // TODO(phase-3 M2): repo.findByCode, ApiException.notFound(code) when missing.
        throw new UnsupportedOperationException("TODO(phase-3 M2): LinkService.get");
    }

    public void delete(String code) {
        // TODO(phase-3 M2): delete from the repo (404 if missing) AND evict it from the cache.
        throw new UnsupportedOperationException("TODO(phase-3 M2): LinkService.delete");
    }

    // ---- redirect hot path (M2 + M3) -----------------------------------------
    public String resolve(String code) {
        // TODO(phase-3 M2/M3): cache-aside lookup (cache -> repo -> cache.put with cacheTtl), 404 if unknown,
        // 410 if expired (also evict it), then repo.recordClick(id, UTC date). A failing recordClick must be
        // logged with LOG.error and must NOT break the redirect. Return the long URL.
        throw new UnsupportedOperationException("TODO(phase-3 M2): LinkService.resolve");
    }

    /** A cache entry must never outlive the link's expiry. */
    public static long cacheTtl(CachedLink link, Instant now) {
        // TODO(phase-3 M3): MAX_CACHE_TTL_SECONDS, but never longer than the seconds left until expiry.
        throw new UnsupportedOperationException("TODO(phase-3 M3): LinkService.cacheTtl");
    }

    // ---- analytics (M4) -------------------------------------------------------
    public List<DailyClicks> dailySeries(Link link, int days) {
        // TODO(phase-3 M4): one DailyClicks per UTC day for the last `days` days (today included), oldest first,
        // zero-filled (repo.dailyClicks only returns days that had clicks).
        throw new UnsupportedOperationException("TODO(phase-3 M4): LinkService.dailySeries");
    }
}
