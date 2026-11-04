package com.relay.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.relay.delivery.AttemptRepository;
import com.relay.delivery.AttemptStatus;
import com.relay.delivery.DeliveryOutcome;
import com.relay.destination.DestinationRepository;
import com.relay.destination.SubscriptionRepository;
import com.relay.publish.PublishResult;
import com.relay.publish.PublishService;

@SpringBootTest
class EventStatusServiceTest {

    @Autowired
    EventStatusService eventStatusService;

    @Autowired
    PublishService publishService;

    @Autowired
    DestinationRepository destinations;

    @Autowired
    SubscriptionRepository subscriptions;

    @Autowired
    AttemptRepository attempts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void get_showsOnePendingDeliveryPerSubscriber() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "status.event." + suffix;

        long destinationId = destinations.insert("status-" + suffix, "https://example.com/status");
        subscriptions.insert(destinationId, eventType);

        PublishResult published = publishService.publish(eventType, "status-key-" + suffix, "{\"a\":1}");

        EventStatusResponse response = eventStatusService.get(published.eventId());

        assertThat(response.eventType()).isEqualTo(eventType);
        assertThat(response.deliveries()).hasSize(1);
        assertThat(response.deliveries().get(0).destinationName()).isEqualTo("status-" + suffix);
        assertThat(response.deliveries().get(0).status()).isEqualTo("PENDING");
        assertThat(response.deliveries().get(0).attemptCount()).isZero();
        assertThat(response.deliveries().get(0).lastError()).isNull();
    }

    @Test
    void get_eventWithNoSubscribers_hasEmptyDeliveries() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        PublishResult published = publishService.publish(
                "status.nobody." + suffix, "status-none-" + suffix, "{\"a\":1}");

        assertThat(eventStatusService.get(published.eventId()).deliveries()).isEmpty();
    }

    @Test
    void get_unknownId_throwsEventNotFound() {
        assertThatThrownBy(() -> eventStatusService.get(-1L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void get_showsTheAttemptHistoryOfADelivery() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "status.attempts." + suffix;

        long destinationId = destinations.insert("status-att-" + suffix, "https://example.com/status");
        subscriptions.insert(destinationId, eventType);
        PublishResult published = publishService.publish(
                eventType, "status-att-key-" + suffix, "{\"a\":1}");
        long deliveryId = findDeliveryId(published.eventId());
        attempts.insert(deliveryId, 1, DeliveryOutcome.ofStatus(500), 12, Instant.now());

        EventStatusResponse response = eventStatusService.get(published.eventId());

        List<AttemptStatus> history = response.deliveries().get(0).attempts();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).attemptNumber()).isEqualTo(1);
        assertThat(history.get(0).outcome()).isEqualTo("FAILURE");
        assertThat(history.get(0).httpStatus()).isEqualTo(500);
        assertThat(history.get(0).errorMessage()).isEqualTo("HTTP 500");
    }

    @Test
    void get_deliveryWithoutAttempts_hasAnEmptyAttemptList() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventType = "status.noattempts." + suffix;

        long destinationId = destinations.insert("status-no-" + suffix, "https://example.com/status");
        subscriptions.insert(destinationId, eventType);
        PublishResult published = publishService.publish(
                eventType, "status-no-key-" + suffix, "{\"a\":1}");

        EventStatusResponse response = eventStatusService.get(published.eventId());

        assertThat(response.deliveries().get(0).attempts()).isEmpty();
    }

    @SuppressWarnings("null")
    private long findDeliveryId(long eventId) {
        String sql = "SELECT id FROM deliveries WHERE event_id = ?";

        return jdbc.queryForObject(sql, Long.class, eventId);
    }
}