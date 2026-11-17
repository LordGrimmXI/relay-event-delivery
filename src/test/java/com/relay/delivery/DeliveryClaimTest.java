package com.relay.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.relay.support.PostgresTestConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfig.class)
public class DeliveryClaimTest {

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
    public void twoWorkersNeverClaimTheSameRow() throws Exception {
        List<Long> createdIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            createdIds.add(createPendingDelivery());
        }

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<List<ClaimedDelivery>> workerA = pool.submit(() -> claimAfterBarrier(barrier));
            Future<List<ClaimedDelivery>> workerB = pool.submit(() -> claimAfterBarrier(barrier));

            List<Long> claimedIds = new ArrayList<>();
            workerA.get(10, TimeUnit.SECONDS).forEach(c -> claimedIds.add(c.id()));
            workerB.get(10, TimeUnit.SECONDS).forEach(c -> claimedIds.add(c.id()));

            assertThat(claimedIds).doesNotHaveDuplicates();

            assertThat(claimedIds).containsExactlyInAnyOrderElementsOf(createdIds);
        } finally {
            pool.shutdownNow();
        }
    }

    @SuppressWarnings("null")
    @Test
    public void claimedRowsAreRunningWithALease() {
        long id = createPendingDelivery();

        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        assertThat(claimed).extracting(ClaimedDelivery::id).containsExactly(id);

        String sql = """
                SELECT status, lease_expires_at > NOW() AS lease_in_future
                FROM deliveries
                WHERE id = ?
                """;
        // queryForMap throws if the row is missing, so this cannot pass with zero rows
        Map<String, Object> row = jdbc.queryForMap(sql, id);

        assertThat(row.get("status")).isEqualTo("RUNNING");
        assertThat(row.get("lease_in_future")).isEqualTo(true);
    }

    @Test
    public void alreadyClaimedRowsAreNotClaimedAgain() {
        createPendingDelivery();

        List<ClaimedDelivery> first = deliveries.claimDue(10, 30);
        List<ClaimedDelivery> second = deliveries.claimDue(10, 30);

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @SuppressWarnings("null")
    @Test
    public void dueRowsAreClaimedButFutureRowsAreNot() {
        long dueId = createPendingDelivery();
        long futureId = createPendingDelivery();

        jdbc.update(
                "UPDATE deliveries SET next_attempt_at = NOW() + INTERVAL '1 hour' WHERE id = ?",
                futureId);

        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        assertThat(claimed).extracting(ClaimedDelivery::id).containsExactly(dueId);
    }

    private List<ClaimedDelivery> claimAfterBarrier(CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        return deliveries.claimDue(20, 30);
    }

    @SuppressWarnings("null")
    private long createPendingDelivery() {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('claim.test', ?, '{}'::jsonb)
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
        return jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId);
    }
}