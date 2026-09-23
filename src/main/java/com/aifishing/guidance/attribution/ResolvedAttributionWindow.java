package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Open recommendation window. {@code recommendationRole} is a slice, not a
 * global identity key for online outcome metrics.
 */
public record ResolvedAttributionWindow(
        UUID deliveredDecisionId,
        AttributionDimension dimension,
        RecommendationRole role,
        GuidanceAction action,
        AttributionWindowKind windowKind,
        boolean followed,
        Instant start,
        Instant end
) {
}
