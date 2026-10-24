package com.relay.event;

import org.springframework.stereotype.Service;

import com.relay.delivery.DeliveryRepository;

@Service
public class EventStatusService {

    private final EventRepository events;
    private final DeliveryRepository deliveries;

    public EventStatusService(EventRepository events, DeliveryRepository deliveries) {
        this.events = events;
        this.deliveries = deliveries;
    }

    public EventStatusResponse get(long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        return new EventStatusResponse(
                event.id(),
                event.eventType(),
                event.createdAt(),
                deliveries.findByEventId(eventId)
        );
    }
}