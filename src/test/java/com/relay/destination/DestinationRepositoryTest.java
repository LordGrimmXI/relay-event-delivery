package com.relay.destination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DestinationRepositoryTest {

    @Autowired
    DestinationRepository destinations;

    @Autowired
    SubscriptionRepository subscriptions;

    @Autowired
    JdbcTemplate jdbc;

    @SuppressWarnings("null")
    @Test
    void findActiveByEventType_returnsOnlyActiveSubscribedDestinations() {
        // Unique names and event type so leftover rows from other runs can't interfere
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "test.event." + suffix;
        String otherType = "other.event." + suffix;

        // Create three destinations, two of which will be subscribed to eventType, and one to otherType.
        Long idOfA = destinations.insert("A-" + suffix, "http://a.example.com");
        Long idOfB = destinations.insert("B-" + suffix, "http://b.example.com");
        Long idOfC = destinations.insert("C-" + suffix, "http://c.example.com");

        // Subscribe A and B to eventType, and C to otherType.
        subscriptions.insert(idOfA, eventType);
        subscriptions.insert(idOfB, eventType);
        subscriptions.insert(idOfC, otherType);

        // Deactivate B
        jdbc.update("UPDATE destinations SET is_active = FALSE WHERE id = ?", idOfB);

        // Call destinations.findActiveByEventType(eventType) and assert
        List<Destination> result = destinations.findActiveByEventType(eventType);

        List<Long> ids = result.stream().map(Destination::id).toList();
        assertThat(ids).containsExactly(idOfA);
    }
}