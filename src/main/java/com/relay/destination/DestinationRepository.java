package com.relay.destination;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DestinationRepository {
    private final JdbcTemplate jdbc;

    public DestinationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insert(String name, String url) {
        String sql = """
                INSERT INTO destinations (name, url)
                VALUES (?, ?)
                RETURNING id
                """;

        return jdbc.queryForObject(sql, Long.class, name, url);
    }

    public List<Destination> findActiveByEventType(String eventType) {
        String sql = """
                SELECT d.id, d.name, d.url, d.is_active, d.created_at
                FROM subscriptions s
                JOIN destinations d ON d.id = s.destination_id
                WHERE s.event_type = ? AND d.is_active = TRUE
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> new Destination(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("url"),
                        rs.getBoolean("is_active"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ),
                eventType
        );
    }

    public Optional<Destination> findById(Long id) {
        String sql = """
                SELECT id, name, url, is_active, created_at
                FROM destinations
                WHERE id = ?
                """;

        List<Destination> results = jdbc.query(
                sql,
                (rs, rowNum) -> new Destination(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("url"),
                        rs.getBoolean("is_active"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ),
                id
        );

        return results.stream().findFirst();
    }

    
    public boolean existsById(Long id) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM destinations WHERE id = ?",
            Integer.class,
            id
        );

        return count != null && count > 0;
    }
    
    public boolean deactivate(Long id) {
        int rowsUpdated = jdbc.update(
            """
            UPDATE destinations
            SET is_active = FALSE
            WHERE id = ? AND is_active = TRUE
            """,
            id
        );

        return rowsUpdated == 1;
    }
}