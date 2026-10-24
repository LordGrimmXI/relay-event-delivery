package com.relay.event;

import java.time.Instant;
import java.util.List;

import com.relay.delivery.DeliveryStatus;

public record EventStatusResponse(
    long eventId,
    String eventType,
    Instant createdAt,
    List<DeliveryStatus> deliveries
) {}