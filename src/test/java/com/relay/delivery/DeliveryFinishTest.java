package com.relay.delivery;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DeliveryFinishTest {

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
    public void markDeliveredFinishesARunningRow() {
        long id = createPendingDelivery();
        deliveries.claimDue(10, 30);

        boolean changed = deliveries.markDelivered(id);

        assertThat(changed).isTrue();
        assertThat(queryString("status", id)).isEqualTo("DELIVERED");
        assertThat(queryInt("attempt_count", id)).isEqualTo(1);
        assertThat(queryBoolean("lease_expires_at IS NULL", id)).isTrue();
    }

    @Test
    public void markForRetryPutsTheRowBackWithAFutureDueTime() {
        long id = createPendingDelivery();
        deliveries.claimDue(10, 30);

        boolean changed = deliveries.markForRetry(id, "HTTP 500", 60);

        assertThat(changed).isTrue();
        assertThat(queryString("status", id)).isEqualTo("PENDING");
        assertThat(queryInt("attempt_count", id)).isEqualTo(1);
        assertThat(queryString("last_error", id)).isEqualTo("HTTP 500");
        assertThat(queryBoolean("next_attempt_at > NOW()", id)).isTrue();
    }

    @Test
    public void finishingARowThatIsNotRunningChangesNothing() {
        long id = createPendingDelivery();

        boolean changed = deliveries.markDelivered(id);

        assertThat(changed).isFalse();
        assertThat(queryString("status", id)).isEqualTo("PENDING");
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
                VALUES ('finish.test', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "finish-" + suffix);

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