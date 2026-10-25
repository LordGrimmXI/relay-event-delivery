package com.relay.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class NaiveClaimRaceDemoTest {

    @Autowired
    private JdbcTemplate jdbc;

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
    public void naiveClaimLetsTwoWorkersTakeTheSameRow() throws Exception {
        long deliveryId = createPendingDelivery();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<List<Long>> workerA = pool.submit(() -> naiveClaim(barrier));
            Future<List<Long>> workerB = pool.submit(() -> naiveClaim(barrier));

            List<Long> allClaims = new ArrayList<>();
            allClaims.addAll(workerA.get(10, TimeUnit.SECONDS));
            allClaims.addAll(workerB.get(10, TimeUnit.SECONDS));

            long timesClaimed = allClaims.stream()
                    .filter(id -> id == deliveryId)
                    .count();

            assertThat(timesClaimed).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    // The broken approach: read first, write later. Another worker can
    // read in the gap between the two steps.
    private List<Long> naiveClaim(CyclicBarrier barrier) throws Exception {
        String selectSql = """
                SELECT id
                FROM deliveries
                WHERE status = 'PENDING' AND next_attempt_at <= NOW()
                ORDER BY id
                """;
        List<Long> ids = jdbc.queryForList(selectSql, Long.class);

        // Both workers wait here until both have finished reading.
        barrier.await(5, TimeUnit.SECONDS);

        String updateSql = """
                UPDATE deliveries
                SET status = 'RUNNING',
                    lease_expires_at = NOW() + INTERVAL '30 seconds'
                WHERE id = ?
                """;
        for (Long id : ids) {
            jdbc.update(updateSql, id);
        }
        return ids;
    }

    @SuppressWarnings("null")
    private long createPendingDelivery() {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('claim.demo', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "claim-" + suffix);

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
        Long deliveryId = jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId);

        return deliveryId;
    }
}