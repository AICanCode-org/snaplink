package org.aicancode.snaplink.link;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PostgreSQL adapter using Spring's JdbcClient (plain SQL, no ORM magic). */
public class JdbcLinkRepository implements LinkRepository {

    static final String COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count";

    private final JdbcClient jdbc;

    public JdbcLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Link insert(String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt) {
        // TODO(phase-3 M2): INSERT ... RETURNING COLUMNS with jdbc.sql(...).params(...).query(JdbcLinkRepository::mapLink)
        // .single(); map Spring's DuplicateKeyException to DuplicateCodeException. Hint: Timestamp.from(instant).
        throw new UnsupportedOperationException("TODO(phase-3 M2): JdbcLinkRepository.insert");
    }

    @Override
    public Optional<Link> findByCode(String code) {
        // TODO(phase-3 M2): SELECT COLUMNS FROM links WHERE code = ?  (.optional())
        throw new UnsupportedOperationException("TODO(phase-3 M2): JdbcLinkRepository.findByCode");
    }

    @Override
    public boolean delete(String code) {
        // TODO(phase-3 M2): DELETE ... and return true if a row was deleted (click_daily cascades).
        throw new UnsupportedOperationException("TODO(phase-3 M2): JdbcLinkRepository.delete");
    }

    @Override
    public void recordClick(long linkId, LocalDate day) {
        // TODO(phase-3 M3): ONE atomic statement that bumps links.click_count and upserts
        // click_daily(link_id, day) (hint: a CTE + INSERT ... ON CONFLICT DO UPDATE).
        throw new UnsupportedOperationException("TODO(phase-3 M3): JdbcLinkRepository.recordClick");
    }

    @Override
    public List<DailyClicks> dailyClicks(long linkId, LocalDate start, LocalDate end) {
        // TODO(phase-3 M4): rows of click_daily for linkId with start <= day <= end, ordered by day.
        throw new UnsupportedOperationException("TODO(phase-3 M4): JdbcLinkRepository.dailyClicks");
    }

    @Override
    public boolean ping() {
        try {
            return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Link mapLink(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        return new Link(rs.getLong("id"), rs.getString("code"), rs.getString("long_url"), rs.getBoolean("is_custom"),
                rs.getTimestamp("created_at").toInstant(), expires == null ? null : expires.toInstant(),
                rs.getLong("click_count"));
    }
}
