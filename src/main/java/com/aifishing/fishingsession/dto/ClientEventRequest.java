package com.aifishing.fishingsession.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ClientEventRequest(
        @NotBlank String clientEventId,
        @NotNull Instant occurredAt
) {
}
