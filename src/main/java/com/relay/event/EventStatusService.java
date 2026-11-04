package com.relay.event;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.relay.delivery.AttemptRepository;
import com.relay.delivery.AttemptStatus;
import com.relay.delivery.DeliveryRepository;
import com.relay.delivery.DeliveryStatus;

@Service
public class EventStatusService {

    private final EventRepository events;
    private final DeliveryRepository deliveries;
    private final AttemptRepository attempts;

    public EventStatusService(
            EventRepository events, DeliveryRepository deliveries, AttemptRepository attempts) {
        this.events = events;
        this.deliveries = deliveries;
        this.attempts = attempts;
    }

    public EventStatusResponse get(long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        // One query for all attempts of the event, grouped by destination.
        Map<Long, List<AttemptStatus>> attemptsByDestination = attempts.findByEventId(eventId);

        List<DeliveryStatus> deliveriesWithAttempts = deliveries.findByEventId(eventId).stream()
                .map(delivery -> delivery.withAttempts(
                        attemptsByDestination.getOrDefault(delivery.destinationId(), List.of())))
                .toList();

        return new EventStatusResponse(
                event.id(),
                event.eventType(),
                event.createdAt(),
                deliveriesWithAttempts
        );
    }
}