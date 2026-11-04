package com.relay.delivery;

import java.time.Instant;
import java.util.List;

public record DeliveryStatus(
        long destinationId,
        String destinationName,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        List<AttemptStatus> attempts
) {

    // Used by DeliveryRepository, which does not load attempts.
    public DeliveryStatus(
            long destinationId,
            String destinationName,
            String status,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError) {
        this(destinationId, destinationName, status, attemptCount, nextAttemptAt, lastError, List.of());
    }

    public DeliveryStatus withAttempts(List<AttemptStatus> attempts) {
        return new DeliveryStatus(destinationId, destinationName, status, attemptCount, nextAttemptAt, lastError, attempts);
    }
}