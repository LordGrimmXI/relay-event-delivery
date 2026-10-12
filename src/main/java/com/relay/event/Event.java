package com.relay.event;

import java.time.Instant;

public record Event(
    Long id,
    String eventType,
    String idempotencyKey,
    String payload,
    Instant createdAt
) {}
