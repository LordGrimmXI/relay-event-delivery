package com.relay.destination;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {
    private final JdbcTemplate jdbc;

    public SubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long destinationId, String eventType) {
        String sql = """
                INSERT INTO subscriptions (destination_id, event_type)
                VALUES(?, ?)
                """;

        jdbc.update(sql, destinationId, eventType);
    }
}
