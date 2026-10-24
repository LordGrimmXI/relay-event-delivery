package com.relay.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private static final RowMapper<Event> EVENT_MAPPER = (rs, rowNum) -> new Event(
            rs.getLong("id"),
            rs.getString("event_type"),
            rs.getString("idempotency_key"),
            rs.getString("payload"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
    );

    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> insertIfAbsent(String eventType, String idempotencyKey, String payload) {
        String sql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """;

        List<Long> ids = jdbc.query(
                sql,
                (rs, rowNum) -> rs.getLong("id"),
                eventType,
                idempotencyKey,
                payload
        );

        return ids.stream().findFirst();
    }

    @SuppressWarnings("null")
    public Optional<Event> findByIdempotencyKey(String idempotencyKey) {
        String sql = """
                SELECT id, event_type, idempotency_key, payload, created_at
                FROM events
                WHERE idempotency_key = ?
                """;

        return jdbc.query(sql, EVENT_MAPPER, idempotencyKey).stream().findFirst();
    }

    @SuppressWarnings("null")
    public Optional<Event> findById(long id) {
        String sql = """
                SELECT id, event_type, idempotency_key, payload, created_at
                FROM events
                WHERE id = ?
                """;

        return jdbc.query(sql, EVENT_MAPPER, id).stream().findFirst();
    }
}