package com.relay.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// One pass of the worker: recover, sweep, then claim-send-record one delivery at a time.
// Deliberately NOT @Transactional: the HTTP call must not hold a database
// connection or row locks. The recorder opens its own short transaction.
@Slf4j
@Component
public class DeliveryWorker {

    private static final int MAX_PER_RUN = 20;
    private static final long LEASE_SECONDS = 30;

    private final DeliveryRepository deliveries;
    private final WebhookClient webhookClient;
    private final DeliveryRecorder recorder;
    private final RetryPolicy retryPolicy;

    public DeliveryWorker(
            DeliveryRepository deliveries,
            WebhookClient webhookClient,
            DeliveryRecorder recorder,
            RetryPolicy retryPolicy) {
        this.deliveries = deliveries;
        this.webhookClient = webhookClient;
        this.recorder = recorder;
        this.retryPolicy = retryPolicy;
    }

    // Returns how many deliveries this pass sent.
    public int runOnce() {
        // First: rows whose lease expired (a crash or a lost update) go back,
        // or are finished at the retry limit.
        int recovered = deliveries.recoverExpiredLeases(retryPolicy.maxAttempts());
        if (recovered > 0) {
            log.warn("Recovered {} deliveries with expired leases", recovered);
        }

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
            // The row stays RUNNING until its lease expires; the next pass recovers it.
            // Log only the id and the exception type: messages can contain destination URLs.
            log.error("Delivery {} failed unexpectedly: {}", delivery.id(), ex.getClass().getSimpleName());
        }
    }

    private void sendAndRecord(ClaimedDelivery delivery) {
        Optional<DeliveryTarget> target = deliveries.findTarget(delivery.id());

        // The attempt starts here, right before the send.
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();

        DeliveryOutcome outcome = target.isPresent()
                ? webhookClient.send(
                        target.get().url(), delivery.eventId(), target.get().payload())
                : DeliveryOutcome.permanent("delivery data missing");

        int durationMs = (int) ((System.nanoTime() - startNanos) / 1_000_000);

        boolean changed = recorder.record(delivery, outcome, startedAt, durationMs);

        if (!changed) {
            log.warn("Delivery {} was no longer owned by this worker; result discarded",
                    delivery.id());
        }
    }
}