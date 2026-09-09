package com.aifishing.planning.ranking;

public record LaunchRecommendationScoreBreakdown(
        double coverage,
        double travelCost,
        double boatFeasibility,
        double accessConfidence,
        double overall,
        String officialName,
        String algorithmVersion
) {
}
