package com.relay.publish;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.relay.delivery.DeliveryRepository;
import com.relay.destination.Destination;
import com.relay.destination.DestinationRepository;
import com.relay.event.EventRepository;

@Service
public class PublishService {
    private final EventRepository events;
    private final DestinationRepository destinations;
    private final DeliveryRepository deliveries;

    public PublishService(EventRepository events,
                          DestinationRepository destinations,
                          DeliveryRepository deliveries) {
        this.events = events;
        this.destinations = destinations;
        this.deliveries = deliveries;
    }

    /**
     * Returns the new event id, or empty if this idempotency key was already used.
     */
    @Transactional
    public Optional<Long> publish(String eventType, String idempotencyKey, String payload) {
        Optional<Long> eventId = events.insertIfAbsent(eventType, idempotencyKey, payload);

        if (eventId.isEmpty()) {
            return Optional.empty();
        }

        List<Destination> targets = destinations.findActiveByEventType(eventType);

        for (Destination destination : targets) {
            deliveries.insert(eventId.get(), destination.id());
        }

        return eventId;
    }
}