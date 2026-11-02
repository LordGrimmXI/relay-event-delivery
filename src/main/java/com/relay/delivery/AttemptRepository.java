package com.relay.delivery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

    // Switch expression with no default: a new Kind is a compile error.
    private String outcomeLabel(Kind kind) {
        return switch (kind) {
            case SUCCESS -> "SUCCESS";
            case RETRYABLE_FAILURE, PERMANENT_FAILURE -> "FAILURE";
        };
    }
}