package com.relay.delivery;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// One pass of the worker: sweep, then claim-send-record one delivery at a time.
// Deliberately NOT @Transactional: the HTTP call must not hold a database
// connection or row locks.
@Slf4j
@Component
public class DeliveryWorker {

    private static final int MAX_PER_RUN = 20;
    private static final long LEASE_SECONDS = 30;

    // Placeholder: next session replaces this with exponential backoff and a retry limit.
    private static final long RETRY_DELAY_SECONDS = 60;

    private final DeliveryRepository deliveries;
    private final WebhookClient webhookClient;

    public DeliveryWorker(DeliveryRepository deliveries, WebhookClient webhookClient) {
        this.deliveries = deliveries;
        this.webhookClient = webhookClient;
    }

    // Returns how many deliveries this pass sent.
    public int runOnce() {
        deliveries.failPendingForInactiveDestinations();

        int processed = 0;
        while (processed < MAX_PER_RUN) {
            // One at a time, so the lease starts right before the send.
            List<ClaimedDelivery> claimed = deliveries.claimDue(1, LEASE_SECONDS);
            if (claimed.isEmpty()) {
                break;
            }

            processed++;
            handle(claimed.get(0));
        }

        return processed;
    }

    private void handle(ClaimedDelivery delivery) {
        try {
            sendAndRecord(delivery);
        } catch (RuntimeException ex) {
            // The row stays RUNNING until lease recovery exists. Log only the id
            // and the exception type: messages can contain destination URLs.
            log.error("Delivery {} failed unexpectedly: {}",
                    delivery.id(), ex.getClass().getSimpleName());
        }
    }

    private void sendAndRecord(ClaimedDelivery delivery) {
        Optional<DeliveryTarget> target = deliveries.findTarget(delivery.id());
        if (target.isEmpty()) {
            deliveries.markPermanentlyFailed(delivery.id(), "delivery data missing");
            return;
        }

        DeliveryOutcome outcome = webhookClient.send(
                target.get().url(), delivery.eventId(), target.get().payload());

        // Switch expression with no default: a new Kind is a compile error.
        boolean changed = switch (outcome.kind()) {
            case SUCCESS -> deliveries.markDelivered(delivery.id());
            case RETRYABLE_FAILURE ->
                    deliveries.markForRetry(delivery.id(), outcome.error(), RETRY_DELAY_SECONDS);
            case PERMANENT_FAILURE ->
                    deliveries.markPermanentlyFailed(delivery.id(), outcome.error());
        };

        if (!changed) {
            log.warn("Delivery {} was no longer RUNNING; result discarded", delivery.id());
        }
    }
}