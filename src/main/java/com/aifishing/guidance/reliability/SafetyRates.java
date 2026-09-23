package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;

/**
 * Candidate vs delivered safety rates. Missing denominators stay {@code null}, never numeric 0.
 */
public record SafetyRates(
        Double candidateUnsafeRate,
        Double candidateInvalidWaypointRate,
        Double validatorInterceptionRate,
        Double unsafeDeliveredRate,
        Double invalidDeliveredWaypointRate
) {
    public static SafetyRates from(SafetyRateCounts counts) {
        return counts.rates();
    }

    public static SafetyRates from(OnlineGuidanceMetrics metrics) {
        if (metrics == null) {
            return new SafetyRates(null, null, null, null, null);
        }
        return new SafetyRates(
                metrics.candidateUnsafeRate(),
                metrics.candidateInvalidWaypointRate(),
                metrics.validatorInterceptionRate(),
                metrics.unsafeDeliveredRate(),
                metrics.invalidDeliveredWaypointRate()
        );
    }

    static Double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (double) numerator / (double) denominator;
    }
}
