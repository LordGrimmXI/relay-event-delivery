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
}