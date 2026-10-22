package com.relay.destination;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DestinationRepository {
    private static final RowMapper<Destination> DESTINATION_MAPPER = (rs, rowNum) -> new Destination(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("url"),
            rs.getBoolean("is_active"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
    );

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

    @SuppressWarnings("null")
    public List<Destination> findActiveByEventType(String eventType) {
        String sql = """
                SELECT d.id, d.name, d.url, d.is_active, d.created_at
                FROM subscriptions s
                JOIN destinations d ON d.id = s.destination_id
                WHERE s.event_type = ? AND d.is_active = TRUE
                """;

        return jdbc.query(sql, DESTINATION_MAPPER, eventType);
    }

    @SuppressWarnings("null")
    public Optional<Destination> findById(long id) {
        String sql = """
                SELECT id, name, url, is_active, created_at
                FROM destinations
                WHERE id = ?
                """;

        return jdbc.query(sql, DESTINATION_MAPPER, id).stream().findFirst();
    }

    public boolean existsById(long id) {
        String sql = "SELECT EXISTS (SELECT 1 FROM destinations WHERE id = ?)";

        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, id));
    }

    public boolean deactivate(long id) {
        String sql = """
                UPDATE destinations
                SET is_active = FALSE
                WHERE id = ?
                """;

        return jdbc.update(sql, id) == 1;
    }
}