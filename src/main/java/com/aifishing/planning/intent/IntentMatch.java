package com.aifishing.planning.intent;

import java.util.UUID;

/**
 * One spatially ranked catalog hit, or an explicit empty-neighborhood fallback.
 * Ranking uses distance and path overlap only.
 */
public record IntentMatch(
        UUID fishingTargetId,
        UUID featureId,
        int rank,
        Double distanceM,
        Double overlapM,
        boolean syntheticFallback
) {
    public static IntentMatch synthetic() {
        return new IntentMatch(null, null, 1, null, null, true);
    }
}
