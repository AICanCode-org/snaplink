package org.aicancode.snaplink.link;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PostgreSQL adapter using Spring's JdbcClient (plain SQL, no ORM magic). */
public class JdbcLinkRepository implements LinkRepository {

    private static final String COLUMNS = "id, code, long_url, is_custom, created_at, expires_at, click_count, last_accessed_at";

    private final JdbcClient jdbc;

    public JdbcLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Link insert(String code, String longUrl, boolean custom, Instant createdAt, Instant expiresAt) {
        try {
            return jdbc.sql("INSERT INTO links (code, long_url, is_custom, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?) RETURNING " + COLUMNS)
                    .params(code, longUrl, custom, Timestamp.from(createdAt),
                            expiresAt == null ? null : Timestamp.from(expiresAt))
                    .query(JdbcLinkRepository::mapLink)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new DuplicateCodeException(code);
        }
    }

    @Override
    public Optional<Link> findByCode(String code) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM links WHERE code = ?")
                .param(code)
                .query(JdbcLinkRepository::mapLink)
                .optional();
    }

    @Override
    public boolean delete(String code) {
        return jdbc.sql("DELETE FROM links WHERE code = ?").param(code).update() > 0;
    }

    @Override
    public void recordClick(long linkId, Instant at) {
        // One statement = one atomic unit: bump the total, remember when, upsert the daily bucket.
        jdbc.sql("""
                WITH bumped AS (
                    UPDATE links SET click_count = click_count + 1, last_accessed_at = ? WHERE id = ? RETURNING id
                )
                INSERT INTO click_daily (link_id, day, clicks)
                SELECT id, ?, 1 FROM bumped
                ON CONFLICT (link_id, day) DO UPDATE SET clicks = click_daily.clicks + 1
                """)
                .params(Timestamp.from(at), linkId, LocalDate.ofInstant(at, ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<DailyClicks> dailyClicks(long linkId, LocalDate start, LocalDate end) {
        return jdbc.sql("SELECT day, clicks FROM click_daily WHERE link_id = ? AND day BETWEEN ? AND ? ORDER BY day")
                .params(linkId, start, end)
                .query((rs, i) -> new DailyClicks(rs.getObject("day", LocalDate.class), rs.getLong("clicks")))
                .list();
    }

    @Override
    public boolean ping() {
        try {
            return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Link mapLink(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp accessed = rs.getTimestamp("last_accessed_at");
        return new Link(rs.getLong("id"), rs.getString("code"), rs.getString("long_url"), rs.getBoolean("is_custom"),
                rs.getTimestamp("created_at").toInstant(), expires == null ? null : expires.toInstant(),
                rs.getLong("click_count"), accessed == null ? null : accessed.toInstant());
    }
}
