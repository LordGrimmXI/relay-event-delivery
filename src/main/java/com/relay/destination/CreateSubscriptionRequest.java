package com.relay.destination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]*", message = "may contain only letters, digits, '.', '_' and '-'")
        String eventType
) {}