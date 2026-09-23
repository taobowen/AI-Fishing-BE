package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record OutcomeAttribution(
        String schemaVersion,
        UUID outcomeEventId,
        UUID fishInteractionId,
        UUID deliveredDecisionId,
        AttributionDimension attributionDimension,
        boolean followedRecommendation,
        RecommendationRole recommendationRole,
        OutcomeKind outcomeKind,
        AttributionWindowKind windowKind,
        Double confidence,
        Instant attributedAt
) {
}
