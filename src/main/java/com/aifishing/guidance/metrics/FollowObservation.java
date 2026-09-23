package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;

import java.util.UUID;

/**
 * Dimension-local follow observation. Decision-level followedPrimary is not used
 * as the only follow flag.
 */
public record FollowObservation(
        UUID deliveredDecisionId,
        AttributionDimension attributionDimension,
        boolean followedRecommendation,
        boolean executedOtherAction
) {
}
