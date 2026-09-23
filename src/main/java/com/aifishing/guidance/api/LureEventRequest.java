package com.aifishing.guidance.api;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.contracts.RetrieveStyle;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LureEventRequest(
        String clientEventId,
        @NotNull Instant occurredAt,
        @NotNull LureFamily lureFamily,
        @NotNull PresentationTechnique presentation,
        Double depthM,
        RetrieveStyle retrieveStyle
) {
}
