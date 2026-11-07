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
public class DeliveryRecoveryTest {

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
    public void expiredLeaseIsPutBackAsPendingAndCountsAsAnAttempt() {
        long id = createPendingDelivery();
        claim(id);
        expireLease(id);

        int recovered = deliveries.recoverExpiredLeases(8);

        assertThat(recovered).isEqualTo(1);
        assertThat(queryString("status", id)).isEqualTo("PENDING");
        assertThat(queryInt("attempt_count", id)).isEqualTo(1);
        assertThat(queryString("last_error", id)).isEqualTo("lease expired");
        assertThat(queryBoolean("lease_expires_at IS NULL", id)).isTrue();
        assertThat(queryBoolean("next_attempt_at <= NOW()", id)).isTrue();
    }

    @Test
    public void leaseThatHasNotExpiredIsLeftAlone() {
        long id = createPendingDelivery();
        claim(id);

        int recovered = deliveries.recoverExpiredLeases(8);

        assertThat(recovered).isZero();
        assertThat(queryString("status", id)).isEqualTo("RUNNING");
        assertThat(queryInt("attempt_count", id)).isZero();
    }

    @Test
    public void expiredLeaseAtTheLimitFailsPermanently() {
        long id = createPendingDelivery();
        setAttemptCount(id, 7);
        claim(id);
        expireLease(id);

        deliveries.recoverExpiredLeases(8);

        assertThat(queryString("status", id)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(queryInt("attempt_count", id)).isEqualTo(8);
        assertThat(queryString("last_error", id))
                .isEqualTo("retry limit reached: lease expired");
    }

    @Test
    public void expiredLeaseOneBeforeTheLimitStillRetries() {
        long id = createPendingDelivery();
        setAttemptCount(id, 6);
        claim(id);
        expireLease(id);

        deliveries.recoverExpiredLeases(8);

        assertThat(queryString("status", id)).isEqualTo("PENDING");
        assertThat(queryInt("attempt_count", id)).isEqualTo(7);
        assertThat(queryString("last_error", id)).isEqualTo("lease expired");
    }

    @SuppressWarnings("null")
    private void claim(long id) {
        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        assertThat(claimed).extracting(ClaimedDelivery::id).contains(id);
    }

    // Simulates time passing: the lease ended a second ago.
    private void expireLease(long id) {
        String sql = """
                UPDATE deliveries
                SET lease_expires_at = NOW() - INTERVAL '1 second'
                WHERE id = ?
                """;

        jdbc.update(sql, id);
    }

    private void setAttemptCount(long id, int count) {
        String sql = "UPDATE deliveries SET attempt_count = ? WHERE id = ?";

        jdbc.update(sql, count, id);
    }

    private String queryString(String column, long id) {
        String sql = "SELECT " + column + " FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    private Integer queryInt(String column, long id) {
        String sql = "SELECT " + column + " FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private Boolean queryBoolean(String expression, long id) {
        String sql = "SELECT " + expression + " FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, Boolean.class, id);
    }

    @SuppressWarnings("null")
    private long createPendingDelivery() {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('recovery.test', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "recovery-" + suffix);

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