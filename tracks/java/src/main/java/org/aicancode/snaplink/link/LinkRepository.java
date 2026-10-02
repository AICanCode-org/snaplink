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

    /** Count one click at {@code at}: total, last-accessed time and the UTC daily bucket. */
    void recordClick(long linkId, Instant at);

    List<DailyClicks> dailyClicks(long linkId, LocalDate start, LocalDate end);

    boolean ping();
}
