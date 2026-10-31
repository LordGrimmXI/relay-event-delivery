package com.relay.delivery;

import java.util.function.DoubleSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Decides when to give up and how long to wait between attempts.
// "attemptsMade" = attempts finished so far, including the one that just failed.
@Component
public class RetryPolicy {

    private static final long BASE_DELAY_SECONDS = 30;
    private static final long MAX_DELAY_SECONDS = 3600;

    private final int maxAttempts;
    private final DoubleSupplier random;

    @Autowired
    public RetryPolicy(@Value("${relay.delivery.max-attempts:8}") int maxAttempts) {
        this(maxAttempts, Math::random);
    }

    public RetryPolicy(int maxAttempts, DoubleSupplier random) {
        this.maxAttempts = maxAttempts;
        this.random = random;
    }

    public boolean isExhausted(int attemptsMade) {
        return attemptsMade >= maxAttempts;
    }

    public long delaySeconds(int attemptsMade) {
        // The exponent is limited so the shift can never overflow.
        int exponent = Math.min(Math.max(attemptsMade - 1, 0), 20);
        long raw = Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS << exponent);

        // Jitter: keep between 50% and 100% of the delay.
        double factor = 0.5 + 0.5 * random.getAsDouble();

        return Math.max(1, Math.round(raw * factor));
    }
}