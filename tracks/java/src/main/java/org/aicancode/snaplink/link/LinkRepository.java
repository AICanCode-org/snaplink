package org.aicancode.snaplink.link;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port. JdbcLinkRepository is the real adapter; tests use an in-memory one. */
public interface LinkRepository {

    Link insert(String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt);

    Optional<Link> findByCode(String code);

    boolean delete(String code);

    void recordClick(long linkId, LocalDate day);

    List<DailyClicks> dailyClicks(long linkId, LocalDate start, LocalDate end);

    boolean ping();
}
