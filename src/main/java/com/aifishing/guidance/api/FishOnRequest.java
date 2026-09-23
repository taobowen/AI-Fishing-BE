package com.aifishing.guidance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record FishOnRequest(
        @NotBlank String clientEventId,
        @NotNull Instant occurredAt,
        @NotNull UUID fishInteractionId
) {
}
