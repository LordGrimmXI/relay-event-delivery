package com.relay.destination;

import java.time.OffsetDateTime;
import java.util.List;

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
}