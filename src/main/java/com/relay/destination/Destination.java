package com.relay.destination;

import java.time.Instant;

public record Destination(
    Long id,
    String name,
    String url,
    boolean isActive,
    Instant createdAt
) {}