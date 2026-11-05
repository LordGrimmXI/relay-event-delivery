package com.relay.delivery;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class StaleWorkerOverwriteDemoTest {

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
    public void aStaleWorkersLateResultCanOverwriteTheNewOwner() {
        long id = createPendingDelivery();

        // Worker A claims the row, then stalls.
        deliveries.claimDue(10, 30);

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

        // Worker B claims the same row. B is now the legitimate owner.
        deliveries.claimDue(10, 30);
        assertThat(findStatus(id)).isEqualTo("RUNNING");

        // Worker A wakes up and reports its old result.
        boolean changed = deliveries.markDelivered(id);

        // Documents the BUG: the guard only checks status, so A's stale result
        // is accepted and B's row is finished by the wrong worker.
        assertThat(changed).isTrue();
        assertThat(findStatus(id)).isEqualTo("DELIVERED");
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
                VALUES ('stale.demo', ?, '{}'::jsonb)
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