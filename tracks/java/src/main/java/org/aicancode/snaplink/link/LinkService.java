package org.aicancode.snaplink.link;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.aicancode.snaplink.cache.LinkCache;
import org.aicancode.snaplink.web.ApiException;
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

    // ---- create -----------------------------------------------------------
    public Link create(Object url, Object alias, Object expiresInDays) {
        String longUrl = Validation.validateUrl(url);
        String customAlias = Validation.validateAlias(alias);
        Integer days = Validation.validateExpiresInDays(expiresInDays);
        Instant now = clock.instant();
        Instant expiresAt = days == null ? null : now.plus(Duration.ofDays(days));

        if (customAlias != null) {
            try {
                return repo.insert(customAlias, longUrl, true, now, expiresAt);
            } catch (DuplicateCodeException e) {
                throw ApiException.aliasTaken(customAlias);
            }
        }
        // Random codes can collide (birthday problem); the DB unique constraint is the
        // source of truth, so we retry a few times instead of "check then insert".
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try {
                return repo.insert(codeGenerator.get(), longUrl, false, now, expiresAt);
            } catch (DuplicateCodeException e) {
                LOG.info("short code collision, retrying");
            }
        }
        throw new IllegalStateException("could not generate a unique short code");
    }

    // ---- read / delete ----------------------------------------------------
    public Link get(String code) {
        return repo.findByCode(code).orElseThrow(() -> ApiException.notFound(code));
    }

    public void delete(String code) {
        if (!repo.delete(code)) {
            throw ApiException.notFound(code);
        }
        cache.delete(code);
    }

    // ---- redirect hot path ------------------------------------------------
    public String resolve(String code) {
        Instant now = clock.instant();
        CachedLink cached = cache.get(code).orElse(null);
        if (cached == null) {
            Link link = repo.findByCode(code).orElseThrow(() -> ApiException.notFound(code));
            cached = new CachedLink(link.id(), link.longUrl(), link.expiresAt());
            long ttl = cacheTtl(cached, now);
            if (ttl > 0) {
                cache.put(code, cached, ttl);
            }
        }
        if (cached.isExpired(now)) {
            cache.delete(code);
            throw ApiException.expired(code);
        }
        try {
            repo.recordClick(cached.id(), LocalDate.ofInstant(now, ZoneOffset.UTC));
        } catch (RuntimeException e) {
            // A lost click must never break a redirect.
            LOG.error("failed to record click for {}", code, e);
        }
        return cached.longUrl();
    }

    /** A cache entry must never outlive the link's expiry. */
    public static long cacheTtl(CachedLink link, Instant now) {
        if (link.expiresAt() == null) {
            return MAX_CACHE_TTL_SECONDS;
        }
        long remaining = Duration.between(now, link.expiresAt()).toSeconds();
        return Math.min(MAX_CACHE_TTL_SECONDS, remaining);
    }

    // ---- analytics --------------------------------------------------------
    public List<DailyClicks> dailySeries(Link link, int days) {
        LocalDate end = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate start = end.minusDays(days - 1L);
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (DailyClicks dc : repo.dailyClicks(link.id(), start, end)) {
            byDay.put(dc.day(), dc.clicks());
        }
        List<DailyClicks> series = new ArrayList<>(days);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            series.add(new DailyClicks(d, byDay.getOrDefault(d, 0L)));
        }
        return series;
    }
}
