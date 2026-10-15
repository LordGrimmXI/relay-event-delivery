package com.relay.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.relay.delivery.DeliveryRepository;
import com.relay.destination.DestinationRepository;
import com.relay.destination.SubscriptionRepository;

@SpringBootTest
class PublishServiceTest {

    @Autowired
    PublishService publishService;

    @Autowired
    DestinationRepository destinations;

    @Autowired
    SubscriptionRepository subscriptions;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    DeliveryRepository deliveries;

    private int countEventsByKey(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE idempotency_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    @Test
    void publish_createsEventAndOneDeliveryPerActiveSubscriber() {
        // Unique names and event type so leftover rows from other runs can't interfere
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "test.event." + suffix;
        String idempotencyKey = "test-key-" + suffix;

        // Create two destinations, both subscribed to eventType.
        Long idOfA = destinations.insert("A-" + suffix, "http://a.example.com");
        Long idOfB = destinations.insert("B-" + suffix, "http://b.example.com");

        // Subscribe A and B to eventType
        subscriptions.insert(idOfA, eventType);
        subscriptions.insert(idOfB, eventType);
        
        Optional<Long> result = publishService.publish(eventType, idempotencyKey, "{\"a\":1}");

        // A new event was created (not treated as a duplicate)
        assertThat(result).isPresent();

        // Exactly one delivery per subscribed active destination
        long eventId = result.get();
        Integer deliveryCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM deliveries WHERE event_id = ?", Integer.class, eventId);
    
        assertThat(deliveryCount).isEqualTo(2);
    }

    @Test
    void publish_rollsBackEventWhenDeliveryInsertFails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "tx.event." + suffix;
        String key = "tx-key-" + suffix;

        Long destId = destinations.insert("tx-" + suffix, "http://tx.example.com");
        subscriptions.insert(destId, eventType);

        // Make every delivery insert throw, AFTER the event row has been inserted.
        doThrow(new IllegalStateException("simulated delivery failure"))
                .when(deliveries).insert(anyLong(), anyLong());

        assertThatThrownBy(() -> publishService.publish(eventType, key, "{\"a\":1}"))
                .isInstanceOf(IllegalStateException.class);

        // The event insert happened first, so if rollback works, it must be gone.
        assertThat(countEventsByKey(key)).isEqualTo(0);
    }
}