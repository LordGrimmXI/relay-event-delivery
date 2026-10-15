package com.relay.publish;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.delivery.DeliveryRepository;
import com.relay.destination.Destination;
import com.relay.destination.DestinationRepository;
import com.relay.event.Event;
import com.relay.event.EventRepository;

@Service
public class PublishService {
    private final EventRepository events;
    private final DestinationRepository destinations;
    private final DeliveryRepository deliveries;
    private final ObjectMapper objectMapper;

    public PublishService(EventRepository events,
                          DestinationRepository destinations,
                          DeliveryRepository deliveries,
                          ObjectMapper objectMapper) {
        this.events = events;
        this.destinations = destinations;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublishResult publish(String eventType, String idempotencyKey, String payload) {
        Optional<Long> eventId = events.insertIfAbsent(eventType, idempotencyKey, payload);

        if (eventId.isEmpty()) {
            return handleDuplicate(idempotencyKey, payload);
        }

        List<Destination> targets = destinations.findActiveByEventType(eventType);

        for (Destination destination : targets) {
            deliveries.insert(eventId.get(), destination.id());
        }

        return new PublishResult(PublishResult.Status.CREATED, eventId.get());
    }

    private PublishResult handleDuplicate(String idempotencyKey, String payload) {
        Event existing = events.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Duplicate key reported but no event found: " + idempotencyKey));

        if (samePayload(existing.payload(), payload)) {
            return new PublishResult(PublishResult.Status.DUPLICATE_SAME_PAYLOAD, existing.id());
        } else {
            return new PublishResult(PublishResult.Status.DUPLICATE_DIFFERENT_PAYLOAD, existing.id());
        }
    }

    private boolean samePayload(String stored, String incoming) {
        try {
            return objectMapper.readTree(stored).equals(objectMapper.readTree(incoming));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse payload JSON", e);
        }
    }
}