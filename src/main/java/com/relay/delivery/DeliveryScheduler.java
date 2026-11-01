package com.relay.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

// Runs the delivery worker on a timer. Only exists when relay.worker.enabled=true,
// so tests and normal startups never run a live worker by accident.
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "relay.worker.enabled", havingValue = "true")
public class DeliveryScheduler {

    private final DeliveryWorker worker;

    public DeliveryScheduler(DeliveryWorker worker) {
        this.worker = worker;
    }

    // fixedDelay: the next pass starts this long after the previous one FINISHES.
    @Scheduled(fixedDelayString = "${relay.worker.poll-interval-ms:5000}")
    public void tick() {
        try {
            int processed = worker.runOnce();
            if (processed > 0) {
                log.info("Worker pass processed {} deliveries", processed);
            }
        } catch (RuntimeException ex) {
            // Exception type only: messages can contain destination URLs.
            log.error("Worker pass failed: {}", ex.getClass().getSimpleName());
        }
    }
}