package com.aifishing.guidance.contracts;

import java.time.Instant;

/**
 * Derived online metrics. Rates stay null when the denominator is unknown.
 * Hard release gates use {@code unsafeDeliveredRate} and {@code invalidDeliveredWaypointRate}.
 */
public record OnlineGuidanceMetrics(
        String schemaVersion,
        Instant windowStart,
        Instant windowEnd,
        AttributionDimension attributionDimension,
        int fishOnSuccessCount,
        int biteSignalOnlyCount,
        int noFishSignalCount,
        int notFollowedCount,
        int unattributedCount,
        Integer followedRecommendationCount,
        Double explicitAcceptanceRate,
        Double observedFollowThroughRate,
        Double rejectRate,
        Double overrideRate,
        Double candidateUnsafeRate,
        Double candidateInvalidWaypointRate,
        Double validatorInterceptionRate,
        Double unsafeDeliveredRate,
        Double invalidDeliveredWaypointRate,
        long effectiveFishingEffortSeconds,
        Double fishOnPerFishingHourAfterRecommendation,
        Double bitePerFishingHourAfterRecommendation
) {
}
