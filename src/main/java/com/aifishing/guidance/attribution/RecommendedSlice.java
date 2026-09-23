package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecommendationRole;

public record RecommendedSlice(
        DeliveredSnapshot snapshot,
        GuidanceAction action,
        AttributionDimension dimension,
        AttributionWindowKind windowKind,
        RecommendationRole role
) {
}
