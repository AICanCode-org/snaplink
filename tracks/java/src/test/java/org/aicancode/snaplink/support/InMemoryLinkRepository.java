package org.aicancode.snaplink.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aicancode.snaplink.link.DailyClicks;
import org.aicancode.snaplink.link.DuplicateCodeException;
import org.aicancode.snaplink.link.Link;
import org.aicancode.snaplink.link.LinkRepository;

/** Test double with the same behaviour as Postgres (unique codes, cascade delete). */
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> links = new HashMap<>();
    private final Map<String, Long> clicks = new HashMap<>(); // key: linkId|day
    private long nextId = 1;
    public boolean failClicks;

    @Override
    public synchronized Link insert(String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt) {
        if (links.containsKey(code)) {
            throw new DuplicateCodeException(code);
        }
        Link link = new Link(nextId++, code, longUrl, custom, createdAt, expiresAt, 0);
        links.put(code, link);
        return link;
    }

    @Override
    public synchronized Optional<Link> findByCode(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public synchronized boolean delete(String code) {
        Link link = links.remove(code);
        if (link == null) {
            return false;
        }
        clicks.keySet().removeIf(k -> k.startsWith(link.id() + "|"));
        return true;
    }

    @Override
    public synchronized void recordClick(long linkId, LocalDate day) {
        if (failClicks) {
            throw new IllegalStateException("db hiccup");
        }
        links.replaceAll((code, l) -> l.id() == linkId
                ? new Link(l.id(), l.code(), l.longUrl(), l.custom(), l.createdAt(), l.expiresAt(), l.clickCount() + 1)
                : l);
        clicks.merge(linkId + "|" + day, 1L, Long::sum);
    }

    @Override
    public synchronized List<DailyClicks> dailyClicks(long linkId, LocalDate start, LocalDate end) {
        List<DailyClicks> result = new ArrayList<>();
        clicks.forEach((key, n) -> {
            String[] parts = key.split("\\|");
            LocalDate day = LocalDate.parse(parts[1]);
            if (Long.parseLong(parts[0]) == linkId && !day.isBefore(start) && !day.isAfter(end)) {
                result.add(new DailyClicks(day, n));
            }
        });
        result.sort(Comparator.comparing(DailyClicks::day));
        return result;
    }

    @Override
    public boolean ping() {
        return true;
    }
}
