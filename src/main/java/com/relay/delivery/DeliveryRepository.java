package com.relay.delivery;

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
}
