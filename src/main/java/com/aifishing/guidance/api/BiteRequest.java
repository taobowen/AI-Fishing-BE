package com.aifishing.guidance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record BiteRequest(
        @NotBlank String clientEventId,
        @NotNull Instant occurredAt,
        UUID fishInteractionId
) {
}
