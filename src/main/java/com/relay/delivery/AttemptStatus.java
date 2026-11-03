package com.relay.delivery;

import java.time.Instant;

public record AttemptStatus(
        int attemptNumber,
        String outcome,
        Integer httpStatus,
        String errorMessage,
        int durationMs,
        Instant startedAt) {
}