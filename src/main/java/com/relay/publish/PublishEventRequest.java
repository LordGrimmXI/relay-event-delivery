package com.relay.publish;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublishEventRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]*", message = "may contain only letters, digits, '.', '_' and '-'")
        String eventType,

        @NotBlank
        @Size(max = 100)
        String idempotencyKey,

        @NotNull
        JsonNode payload
) {}