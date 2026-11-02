package com.relay.delivery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class DeliveryRecorderTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private DeliveryRecorder recorder;

    @BeforeEach
    public void parkLeftoverPendingRows() {
        String sql = """
                UPDATE deliveries
                SET status = 'PERMANENTLY_FAILED'
                WHERE status = 'PENDING'
                """;
        jdbc.update(sql);
    }

    @Test
    public void successWritesASuccessAttempt() {
        long id = createPendingDelivery();
        ClaimedDelivery claimed = claim(id);

        boolean changed = recorder.record(claimed, DeliveryOutcome.ofStatus(200), Instant.now(), 42);

        assertThat(changed).isTrue();
        assertThat(findStatus(id)).isEqualTo("DELIVERED");
        Map<String, Object> attempt = findOnlyAttempt(id);
        assertThat(attempt.get("attempt_number")).isEqualTo(1);
        assertThat(attempt.get("outcome")).isEqualTo("SUCCESS");
        assertThat(attempt.get("http_status")).isEqualTo(200);
        assertThat(attempt.get("error_message")).isNull();
        assertThat(attempt.get("duration_ms")).isEqualTo(42);
    }

    @Test
    public void retryableFailureWritesAFailureAttemptAndKeepsTheRowPending() {
        long id = createPendingDelivery();
        ClaimedDelivery claimed = claim(id);

        recorder.record(claimed, DeliveryOutcome.ofStatus(500), Instant.now(), 7);

        assertThat(findStatus(id)).isEqualTo("PENDING");
        Map<String, Object> attempt = findOnlyAttempt(id);
        assertThat(attempt.get("outcome")).isEqualTo("FAILURE");
        assertThat(attempt.get("http_status")).isEqualTo(500);
        assertThat(attempt.get("error_message")).isEqualTo("HTTP 500");
    }

    @Test
    public void aDiscardedResultWritesNoAttempt() {
        long id = createPendingDelivery();
        ClaimedDelivery claimed = claim(id);
        // Simulates the row no longer being RUNNING (someone else changed it).
        jdbc.update("UPDATE deliveries SET status = 'PENDING' WHERE id = ?", id);

        boolean changed = recorder.record(claimed, DeliveryOutcome.ofStatus(200), Instant.now(), 5);

        assertThat(changed).isFalse();
        assertThat(countAttempts(id)).isEqualTo(0);
        assertThat(findStatus(id)).isEqualTo("PENDING");
    }

    @Test
    public void ifTheAttemptInsertFailsTheDeliveryUpdateIsRolledBack() {
        long id = createPendingDelivery();
        ClaimedDelivery claimed = claim(id);
        // Occupies attempt number 1, so the recorder's insert violates the UNIQUE constraint.
        String sql = """
                INSERT INTO delivery_attempts
                    (delivery_id, attempt_number, outcome, duration_ms, started_at)
                VALUES (?, 1, 'FAILURE', 0, NOW())
                """;
        jdbc.update(sql, id);

        assertThatThrownBy(() ->
                recorder.record(claimed, DeliveryOutcome.ofStatus(200), Instant.now(), 5))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(findStatus(id)).isEqualTo("RUNNING");
        assertThat(countAttempts(id)).isEqualTo(1);
    }

    private ClaimedDelivery claim(long id) {
        return deliveries.claimDue(10, 30).stream()
                .filter(c -> c.id() == id)
                .findFirst()
                .orElseThrow();
    }

    private String findStatus(long id) {
        String sql = "SELECT status FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    private Integer countAttempts(long id) {
        String sql = "SELECT COUNT(*) FROM delivery_attempts WHERE delivery_id = ?";

        return jdbc.queryForObject(sql, Integer.class, id);
    }

    // queryForMap fails unless there is exactly one row, which is what we want here.
    private Map<String, Object> findOnlyAttempt(long id) {
        String sql = """
                SELECT attempt_number, outcome, http_status, error_message, duration_ms
                FROM delivery_attempts
                WHERE delivery_id = ?
                """;

        return jdbc.queryForMap(sql, id);
    }

    @SuppressWarnings("null")
    private long createPendingDelivery() {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('recorder.test', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "recorder-" + suffix);

        String destinationSql = """
                INSERT INTO destinations (name, url)
                VALUES (?, 'https://example.com/hook')
                RETURNING id
                """;
        Long destinationId = jdbc.queryForObject(destinationSql, Long.class, "dest-" + suffix);

        String deliverySql = """
                INSERT INTO deliveries (event_id, destination_id)
                VALUES (?, ?)
                RETURNING id
                """;
        return jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId);
    }
}