package com.relay.delivery;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {
    private final JdbcTemplate jdbc;

    public DeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long eventId, long destinationId) {
        String sql = "INSERT INTO deliveries (event_id, destination_id) VALUES (?, ?)";

        jdbc.update(sql, eventId, destinationId);
    }

    public List<DeliveryStatus> findByEventId(long eventId) {
        String sql = """
                SELECT del.destination_id, dest.name, del.status, del.attempt_count,
                       del.next_attempt_at, del.last_error
                FROM deliveries del
                JOIN destinations dest ON dest.id = del.destination_id
                WHERE del.event_id = ?
                ORDER BY del.id
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> new DeliveryStatus(
                        rs.getLong("destination_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("attempt_count"),
                        rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                        rs.getString("last_error")
                ),
                eventId
        );
    }

    // Atomically claims up to `limit` due deliveries for ACTIVE destinations:
    // marks them RUNNING with a lease and returns exactly the rows this caller won.
    public List<ClaimedDelivery> claimDue(int limit, long leaseSeconds) {
        String sql = """
                UPDATE deliveries
                SET status = 'RUNNING',
                    lease_expires_at = NOW() + (? * INTERVAL '1 second')
                WHERE id IN (
                    SELECT id
                    FROM deliveries
                    WHERE status = 'PENDING' AND next_attempt_at <= NOW()
                      AND EXISTS (
                          SELECT 1
                          FROM destinations d
                          WHERE d.id = deliveries.destination_id AND d.is_active
                      )
                    ORDER BY next_attempt_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, event_id, destination_id, attempt_count
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> new ClaimedDelivery(
                        rs.getLong("id"),
                        rs.getLong("event_id"),
                        rs.getLong("destination_id"),
                        rs.getInt("attempt_count")
                ),
                leaseSeconds,
                limit
        );
    }

    // Marks PENDING deliveries whose destination is inactive as finished,
    // so the status endpoint tells the truth. Returns how many rows changed.
    public int failPendingForInactiveDestinations() {
        String sql = """
                UPDATE deliveries
                SET status = 'PERMANENTLY_FAILED',
                    last_error = 'destination deactivated'
                WHERE status = 'PENDING'
                    AND EXISTS (
                        SELECT 1
                        FROM destinations d
                        WHERE d.id = deliveries.destination_id AND NOT d.is_active
                    )
                """;

        return jdbc.update(sql);
    }
}