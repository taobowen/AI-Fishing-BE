package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserActionEvent(
        String schemaVersion,
        Instant occurredAt,
        UUID deliveredDecisionId,
        boolean followedPrimary,
        GuidanceAction actualAction,
        Map<String, Object> payload,
        Boolean followedRecommendation,
        RecommendationRole recommendationRole
) {
}
