package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;

import java.time.Instant;
import java.util.UUID;

public record RecommendationEffortWindow(
        UUID fishingSessionId,
        UUID deliveredDecisionId,
        AttributionDimension attributionDimension,
        Instant start,
        Instant end
) {
}
