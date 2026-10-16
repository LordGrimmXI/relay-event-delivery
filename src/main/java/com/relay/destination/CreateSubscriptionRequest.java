package com.relay.destination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionRequest(
    @NotBlank
    @Size(max = 100)
    String eventType
) {}
