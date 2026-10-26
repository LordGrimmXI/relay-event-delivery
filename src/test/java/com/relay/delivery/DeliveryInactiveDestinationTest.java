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
public class DeliveryInactiveDestinationTest {

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

    @SuppressWarnings("null")
    @Test
    public void inactiveDestinationIsNotClaimed() {
        long inactiveId = createPendingDelivery(false);

        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        assertThat(claimed).extracting(ClaimedDelivery::id).doesNotContain(inactiveId);
        assertThat(findStatus(inactiveId)).isEqualTo("PENDING");
    }

    @SuppressWarnings("null")
    @Test
    public void activeDestinationIsStillClaimed() {
        long activeId = createPendingDelivery(true);

        List<ClaimedDelivery> claimed = deliveries.claimDue(10, 30);

        assertThat(claimed).extracting(ClaimedDelivery::id).contains(activeId);
    }

    @Test
    public void sweepFailsPendingDeliveryOfInactiveDestination() {
        @SuppressWarnings("unused")
        long inactiveId = createPendingDelivery(false);

        deliveries.failPendingForInactiveDestinations();

        assertThat(findStatus(inactiveId)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(findLastError(inactiveId)).isEqualTo("destination deactivated");
    }

    @Test
    public void sweepLeavesActiveDestinationAlone() {
        long activeId = createPendingDelivery(true);

        deliveries.failPendingForInactiveDestinations();

        assertThat(findStatus(activeId)).isEqualTo("PENDING");
    }

    private String findStatus(long deliveryId) {
        String sql = "SELECT status FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, deliveryId);
    }

    @SuppressWarnings("unused")
    private String findLastError(long deliveryId) {
        String sql = "SELECT last_error FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, deliveryId);
    }

    @SuppressWarnings("null")
    private long createPendingDelivery(boolean destinationActive) {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('inactive.test', ?, '{}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "inactive-" + suffix);

        String destinationSql = """
                INSERT INTO destinations (name, url, is_active)
                VALUES (?, 'https://example.com/hook', ?)
                RETURNING id
                """;
        Long destinationId = jdbc.queryForObject(
                destinationSql, Long.class, "dest-" + suffix, destinationActive);

        String deliverySql = """
                INSERT INTO deliveries (event_id, destination_id)
                VALUES (?, ?)
                RETURNING id
                """;
        return jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId);
    }
}