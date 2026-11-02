package com.relay.delivery;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Records the result of one attempt: the guarded delivery update and the
// attempt history row succeed or fail together.
@Service
public class DeliveryRecorder {

    private final DeliveryRepository deliveries;
    private final AttemptRepository attempts;
    private final RetryPolicy retryPolicy;

    public DeliveryRecorder(
            DeliveryRepository deliveries, AttemptRepository attempts, RetryPolicy retryPolicy) {
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.retryPolicy = retryPolicy;
    }

    // Returns false (and writes nothing) if the row was no longer RUNNING.
    @Transactional
    public boolean record(
            ClaimedDelivery delivery, DeliveryOutcome outcome, Instant startedAt, int durationMs) {
        // The claimed row carries the count from before this attempt.
        int attemptNumber = delivery.attemptCount() + 1;

        // Switch expression with no default: a new Kind is a compile error.
        boolean changed = switch (outcome.kind()) {
            case SUCCESS -> deliveries.markDelivered(delivery.id());
            case RETRYABLE_FAILURE -> retryOrGiveUp(delivery, attemptNumber, outcome);
            case PERMANENT_FAILURE ->
                    deliveries.markPermanentlyFailed(delivery.id(), outcome.error());
        };

        if (changed) {
            attempts.insert(delivery.id(), attemptNumber, outcome, durationMs, startedAt);
        }

        return changed;
    }

    private boolean retryOrGiveUp(
            ClaimedDelivery delivery, int attemptNumber, DeliveryOutcome outcome) {
        if (retryPolicy.isExhausted(attemptNumber)) {
            return deliveries.markPermanentlyFailed(
                    delivery.id(), "retry limit reached: " + outcome.error());
        }

        return deliveries.markForRetry(
                delivery.id(), outcome.error(), retryPolicy.delaySeconds(attemptNumber));
    }
}