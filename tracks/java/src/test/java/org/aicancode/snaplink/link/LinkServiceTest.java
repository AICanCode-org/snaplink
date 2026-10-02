package org.aicancode.snaplink.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import org.aicancode.snaplink.support.InMemoryLinkCache;
import org.aicancode.snaplink.support.InMemoryLinkRepository;
import org.aicancode.snaplink.support.MutableClock;
import org.aicancode.snaplink.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkServiceTest {

    private InMemoryLinkRepository repo;
    private InMemoryLinkCache cache;
    private MutableClock clock;
    private LinkService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryLinkRepository();
        cache = new InMemoryLinkCache();
        clock = new MutableClock();
        service = new LinkService(repo, cache, clock, new ShortCodeGenerator());
    }

    private static int status(Runnable r) {
        try {
            r.run();
            return -1;
        } catch (ApiException e) {
            return e.status();
        }
    }

    @Test
    void createsRandomLink() {
        Link link = service.create("https://www.aicancode.org", null, null);
        assertThat(link.code()).hasSize(7);
        assertThat(link.custom()).isFalse();
        assertThat(link.createdAt()).isEqualTo(clock.instant());
        assertThat(link.expiresAt()).isNull();
    }

    @Test
    void createsWithAliasAndExpiry() {
        Link link = service.create("https://www.aicancode.org", "aicancode", 30);
        assertThat(link.code()).isEqualTo("aicancode");
        assertThat(link.custom()).isTrue();
        assertThat(link.expiresAt()).isEqualTo(Instant.parse("2026-11-01T09:30:00Z"));
    }

    @Test
    void duplicateAliasIs409() {
        service.create("https://a.com", "taken", null);
        assertThat(status(() -> service.create("https://b.com", "taken", null))).isEqualTo(409);
    }

    @Test
    void retriesRandomCodeCollisions() {
        Iterator<String> codes = List.of("SAME123", "SAME123", "OTHER45").iterator();
        LinkService s = new LinkService(repo, cache, clock, codes::next);
        assertThat(s.create("https://a.com", null, null).code()).isEqualTo("SAME123");
        assertThat(s.create("https://b.com", null, null).code()).isEqualTo("OTHER45");
    }

    @Test
    void givesUpAfterTooManyCollisions() {
        LinkService s = new LinkService(repo, cache, clock, () -> "SAME123");
        s.create("https://a.com", null, null);
        assertThatThrownBy(() -> s.create("https://b.com", null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveRecordsClickAndPopulatesCache() {
        Link link = service.create("https://www.aicancode.org", null, null);
        assertThat(service.resolve(link.code())).isEqualTo("https://www.aicancode.org");
        assertThat(cache.store).containsKey(link.code());
        assertThat(repo.findByCode(link.code()).orElseThrow().clickCount()).isEqualTo(1);
    }

    @Test
    void secondHitIsServedFromCache() {
        Link link = service.create("https://www.aicancode.org", null, null);
        service.resolve(link.code());
        repo.delete(link.code()); // the DB no longer knows it; only the cache does
        assertThat(service.resolve(link.code())).isEqualTo("https://www.aicancode.org");
    }

    @Test
    void unknownCodeIs404() {
        assertThat(status(() -> service.resolve("nope123"))).isEqualTo(404);
    }

    @Test
    void expiredLinkIs410EvenWhenCached() {
        Link link = service.create("https://a.com", null, 1);
        service.resolve(link.code());
        clock.advance(Duration.ofDays(1)); // exactly at expiry -> expired
        assertThat(status(() -> service.resolve(link.code()))).isEqualTo(410);
        assertThat(cache.store).doesNotContainKey(link.code());
        assertThat(service.get(link.code()).clickCount()).isEqualTo(1); // the expired attempt was not counted
    }

    @Test
    void cacheTtlNeverOutlivesExpiry() {
        Instant now = clock.instant();
        assertThat(LinkService.cacheTtl(new CachedLink(1, "https://a.com", now.plusSeconds(90)), now)).isEqualTo(90);
        assertThat(LinkService.cacheTtl(new CachedLink(2, "https://b.com", null), now))
                .isEqualTo(LinkService.MAX_CACHE_TTL_SECONDS);
    }

    @Test
    void deleteRemovesLinkAndCacheEntry() {
        Link link = service.create("https://a.com", null, null);
        service.resolve(link.code());
        service.delete(link.code());
        assertThat(cache.store).doesNotContainKey(link.code());
        assertThat(status(() -> service.get(link.code()))).isEqualTo(404);
    }

    @Test
    void deleteUnknownIs404() {
        assertThat(status(() -> service.delete("missing"))).isEqualTo(404);
    }

    @Test
    void failingClickWriteDoesNotBreakRedirect() {
        Link link = service.create("https://a.com", null, null);
        repo.failClicks = true;
        assertThat(service.resolve(link.code())).isEqualTo("https://a.com");
    }

    @Test
    void statsAreZeroFilledOldestFirst() {
        Link link = service.create("https://a.com", null, null);
        service.resolve(link.code());
        clock.advance(Duration.ofDays(2));
        service.resolve(link.code());
        service.resolve(link.code());
        Link found = service.get(link.code());
        List<DailyClicks> daily = service.dailySeries(found, 7);
        assertThat(found.clickCount()).isEqualTo(3);
        assertThat(daily).hasSize(7);
        assertThat(daily.get(6)).isEqualTo(new DailyClicks(LocalDate.parse("2026-10-04"), 2));
        assertThat(daily.get(4)).isEqualTo(new DailyClicks(LocalDate.parse("2026-10-02"), 1));
    }
}
