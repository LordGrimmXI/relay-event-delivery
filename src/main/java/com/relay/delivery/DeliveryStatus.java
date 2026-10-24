package com.relay.delivery;

import java.time.Instant;

public record DeliveryStatus(
    long destinationId,
    String destinationName,
    String status,
    int attemptCount,
    Instant nextAttemptAt,
    String lastError
) {}