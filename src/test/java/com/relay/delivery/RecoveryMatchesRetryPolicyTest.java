package com.relay.delivery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RecoveryMatchesRetryPolicyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeliveryRepository deliveries;

    @BeforeEach
    public void parkLeftoverRows() {
        String sql = """
                UPDATE deliveries
                SET status = 'PERMANENTLY_FAILED'
                WHERE status IN ('PENDING', 'RUNNING')
                """;
        jdbc.update(sql);
    }

    @Test
    public void sqlAndJavaAgreeOnWhenToGiveUp() {
        RetryPolicy policy = new RetryPolicy(8, () -> 1.0);

        Map<Integer, Long> idsByCount = new LinkedHashMap<>();
        for (int count = 0; count <= 9; count++) {
            idsByCount.put(count, createExpiredRunningDelivery(count));
        }

        deliveries.recoverExpiredLeases(policy.maxAttempts());

        idsByCount.forEach((count, id) -> {
            boolean sqlGaveUp = findStatus(id).equals("PERMANENTLY_FAILED");
            boolean javaSaysExhausted = policy.isExhausted(count + 1);

            assertThat(sqlGaveUp)
                    .as("row that had attempt count %s", count)
                    .isEqualTo(javaSaysExhausted);
        });
    }

    private String findStatus(long id) {
        String sql = "SELECT status FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    // A RUNNING row whose lease ended a second ago, with the given attempt count.
    @SuppressWarnings("null")
    private long createExpiredRunningDelivery(int attemptCount) {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('agree.test', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "agree-" + suffix);

        String destinationSql = """
                INSERT INTO destinations (name, url)
                VALUES (?, 'https://example.com/hook')
                RETURNING id
                """;
        Long destinationId = jdbc.queryForObject(destinationSql, Long.class, "dest-" + suffix);

        String deliverySql = """
                INSERT INTO deliveries
                    (event_id, destination_id, status, attempt_count, lease_expires_at)
                VALUES (?, ?, 'RUNNING', ?, NOW() - INTERVAL '1 second')
                RETURNING id
                """;
        return jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId, attemptCount);
    }
}