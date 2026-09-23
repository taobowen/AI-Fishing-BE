package com.aifishing.guidance.reliability;

import com.aifishing.guidance.GuidanceProperties;

/**
 * Candidate-rate thresholds. Default is report-only; delivered rates stay a hard gate.
 */
public record ReliabilityGatePolicy(
        double candidateUnsafeRateMax,
        double candidateInvalidWaypointRateMax,
        double validatorInterceptionRateMax,
        boolean candidateRatesBlocking
) {
    public static ReliabilityGatePolicy reportOnly() {
        return new ReliabilityGatePolicy(0.0, 0.0, 0.0, false);
    }

    public static ReliabilityGatePolicy from(GuidanceProperties properties) {
        if (properties == null || properties.getReliability() == null) {
            return reportOnly();
        }
        GuidanceProperties.Reliability reliability = properties.getReliability();
        return new ReliabilityGatePolicy(
                reliability.getCandidateUnsafeRateMax(),
                reliability.getCandidateInvalidWaypointRateMax(),
                reliability.getValidatorInterceptionRateMax(),
                reliability.isCandidateRatesBlocking()
        );
    }
}
