package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One persisted attribution row. Role is slice metadata and is ignored by
 * {@link OnlineOutcomeIdentity} dedupe.
 */
public record OnlineOutcomeEvent(
        UUID fishingSessionId,
        UUID deliveredDecisionId,
        AttributionDimension attributionDimension,
        RecommendationRole recommendationRole,
        UUID fishInteractionId,
        OutcomeKind outcomeKind,
        boolean followedRecommendation,
        Instant attributedAt
) {
}
