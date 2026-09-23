package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Instant;
import java.util.UUID;

public record ComputedAttribution(
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
