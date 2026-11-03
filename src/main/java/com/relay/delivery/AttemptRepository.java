package com.relay.delivery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.relay.delivery.DeliveryOutcome.Kind;

@Repository
public class AttemptRepository {
    private final JdbcTemplate jdbc;

    public AttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Appends one history row. http_status and error_message may be null.
    public void insert(
            long deliveryId,
            int attemptNumber,
            DeliveryOutcome outcome,
            int durationMs,
            Instant startedAt) {
        String sql = """
                INSERT INTO delivery_attempts
                    (delivery_id, attempt_number, outcome, http_status,
                     error_message, duration_ms, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        jdbc.update(
                sql,
                deliveryId,
                attemptNumber,
                outcomeLabel(outcome.kind()),
                outcome.httpStatus(),
                outcome.error(),
                durationMs,
                OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC)
        );
    }

    // All attempts of one event, grouped by destination id and in attempt order.
    // A destination with no attempts has no entry in the map.
    public Map<Long, List<AttemptStatus>> findByEventId(long eventId) {
        String sql = """
                SELECT del.destination_id, att.attempt_number, att.outcome, att.http_status,
                       att.error_message, att.duration_ms, att.started_at
                FROM delivery_attempts att
                JOIN deliveries del ON del.id = att.delivery_id
                WHERE del.event_id = ?
                ORDER BY del.id, att.attempt_number
                """;

        Map<Long, List<AttemptStatus>> byDestination = new LinkedHashMap<>();

        jdbc.query(
                sql,
                rs -> {
                    byDestination
                            .computeIfAbsent(rs.getLong("destination_id"), id -> new ArrayList<>())
                            .add(new AttemptStatus(
                                    rs.getInt("attempt_number"),
                                    rs.getString("outcome"),
                                    rs.getObject("http_status", Integer.class),
                                    rs.getString("error_message"),
                                    rs.getInt("duration_ms"),
                                    rs.getObject("started_at", OffsetDateTime.class).toInstant()
                            ));
                },
                eventId
        );

        return byDestination;
    }

    // Switch expression with no default: a new Kind is a compile error.
    private String outcomeLabel(Kind kind) {
        return switch (kind) {
            case SUCCESS -> "SUCCESS";
            case RETRYABLE_FAILURE, PERMANENT_FAILURE -> "FAILURE";
        };
    }
}