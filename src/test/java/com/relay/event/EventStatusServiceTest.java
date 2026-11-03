package com.relay.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        assertThatThrownBy(() -> eventStatusService.get(-1L))
                .isInstanceOf(EventNotFoundException.class);
    }
}