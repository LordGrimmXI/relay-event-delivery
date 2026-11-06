package com.relay.delivery;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class StaleWorkerGuardTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeliveryRepository deliveries;

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
    public void aStaleWorkersLateResultIsRejectedAndTheCurrentOwnerStillWins() {
        long id = createPendingDelivery();

        // Worker A claims the row (it sees attempt count 0), then stalls.
        ClaimedDelivery claimedByA = claim(id);
        assertThat(claimedByA.attemptCount()).isZero();

        // The lease expires and recovery puts the row back (simulated with SQL,
        // because the real recovery statement does not exist yet).
        String recoverSql = """
                UPDATE deliveries
                SET status = 'PENDING',
                    lease_expires_at = NULL,
                    attempt_count = attempt_count + 1
                WHERE id = ?
                """;
        jdbc.update(recoverSql, id);

        // Worker B claims the same row (it sees attempt count 1).
        ClaimedDelivery claimedByB = claim(id);
        assertThat(claimedByB.attemptCount()).isEqualTo(1);

        // Worker A wakes up and reports its old result: rejected.
        boolean staleChanged = deliveries.markDelivered(id, claimedByA.attemptCount());
        assertThat(staleChanged).isFalse();
        assertThat(findStatus(id)).isEqualTo("RUNNING");

        // Worker B reports its result: accepted.
        boolean ownerChanged = deliveries.markDelivered(id, claimedByB.attemptCount());
        assertThat(ownerChanged).isTrue();
        assertThat(findStatus(id)).isEqualTo("DELIVERED");
    }

    private ClaimedDelivery claim(long id) {
        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        return claimed.stream()
                .filter(c -> c.id() == id)
                .findFirst()
                .orElseThrow();
    }

    private String findStatus(long id) {
        String sql = "SELECT status FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    @SuppressWarnings("null")
    private long createPendingDelivery() {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('stale.guard', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "stale-" + suffix);

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