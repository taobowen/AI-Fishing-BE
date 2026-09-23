package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ObservedUserAction(
        UUID deliveredDecisionId,
        Instant occurredAt,
        GuidanceAction actualAction,
        boolean followedRecommendation,
        RecommendationRole recommendationRole,
        boolean followedPrimary,
        Map<String, Object> payload
) {
}
