package com.aifishing.planning.ranking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreBreakdown(
        double strategyMatch,
        double depthMatch,
        double featureConfidence,
        double timeWindowMatch,
        double gearCompatibility,
        double weatherCompatibility,
        double travelAccess,
        Double historicalPerformance,
        Double historicalEvidenceConfidence,
        Double intrinsic,
        Double strategyTimeEffect,
        Double solarInfluenceStrength,
        Double orientationExposure,
        Double solarFishingEffect,
        String windOrientation,
        Double windFishingEffect,
        Double temperatureEffect,
        Double boatWeatherPenalty,
        Double waitPenalty,
        Double finalTimeAdjustedUtility
) {
    public ScoreBreakdown(
            double strategyMatch,
            double depthMatch,
            double featureConfidence,
            double timeWindowMatch,
            double gearCompatibility,
            double weatherCompatibility,
            double travelAccess,
            Double historicalPerformance,
            Double historicalEvidenceConfidence
    ) {
        this(
                strategyMatch,
                depthMatch,
                featureConfidence,
                timeWindowMatch,
                gearCompatibility,
                weatherCompatibility,
                travelAccess,
                historicalPerformance,
                historicalEvidenceConfidence,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public double historicalPerformanceOrNeutral() {
        return historicalPerformance == null ? 0.5 : historicalPerformance;
    }

    public double historicalEvidenceConfidenceOrZero() {
        return historicalEvidenceConfidence == null ? 0.0 : historicalEvidenceConfidence;
    }

    public ScoreBreakdown withTimeAdjusted(
            double intrinsicScore,
            double strategyTimeEffectValue,
            double solarInfluence,
            double orientationExposureValue,
            double solarFishing,
            String windOrientationValue,
            double windFishing,
            double temperature,
            double boatPenalty,
            double waitPenaltyValue,
            double finalUtility
    ) {
        return new ScoreBreakdown(
                strategyMatch,
                depthMatch,
                featureConfidence,
                timeWindowMatch,
                gearCompatibility,
                weatherCompatibility,
                travelAccess,
                historicalPerformance,
                historicalEvidenceConfidence,
                intrinsicScore,
                strategyTimeEffectValue,
                solarInfluence,
                orientationExposureValue,
                solarFishing,
                windOrientationValue,
                windFishing,
                temperature,
                boatPenalty,
                waitPenaltyValue,
                finalUtility
        );
    }
}
