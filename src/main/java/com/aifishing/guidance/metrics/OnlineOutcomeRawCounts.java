package com.aifishing.guidance.metrics;

/**
 * SUM-able raw counts for {@code guidance_online_metric_rollups}.
 * Rates are never stored here.
 */
public record OnlineOutcomeRawCounts(
        int fishOnSuccessCount,
        int biteSignalOnlyCount,
        int noFishSignalCount,
        int notFollowedCount,
        int unattributedCount,
        int followedRecommendationCount,
        int explicitAcceptedCount,
        int explicitPartialCount,
        int rejectCount,
        int overrideCount,
        int feedbackCount,
        int followThroughEligibleCount,
        int overrideEligibleCount,
        long effectiveFishingEffortSeconds,
        int landedCount,
        int lostCount,
        OnlineOutcomeSafetyCounts safety
) {
    public OnlineOutcomeSafetyCounts safetyOrEmpty() {
        return safety == null ? OnlineOutcomeSafetyCounts.empty() : safety;
    }
}
