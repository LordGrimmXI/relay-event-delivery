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

    @Test
    void findById_returnsInsertedDestination() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = destinations.insert("find-" + suffix, "https://example.com/find");

        assertThat(destinations.findById(id)).isPresent();
        assertThat(destinations.findById(id).get().name()).isEqualTo("find-" + suffix);
        assertThat(destinations.findById(id).get().isActive()).isTrue();
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertThat(destinations.findById(-1L)).isEmpty();
    }

    @Test
    void existsById_trueForKnownFalseForUnknown() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = destinations.insert("exists-" + suffix, "https://example.com/exists");

        assertThat(destinations.existsById(id)).isTrue();
        assertThat(destinations.existsById(-1L)).isFalse();
    }

    @Test
    void deactivate_isIdempotent_andFalseForUnknownId() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = destinations.insert("deact-" + suffix, "https://example.com/deact");

        assertThat(destinations.deactivate(id)).isTrue();
        assertThat(destinations.deactivate(id)).isTrue();
        assertThat(destinations.findById(id).get().isActive()).isFalse();
        assertThat(destinations.deactivate(-1L)).isFalse();
    }
}