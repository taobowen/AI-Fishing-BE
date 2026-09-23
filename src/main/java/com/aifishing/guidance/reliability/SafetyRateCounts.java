package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.DecisionValidationResult;

/**
 * Raw candidate / delivered safety counts. Rates are derived and stay null when the
 * denominator is unknown.
 */
public record SafetyRateCounts(
        long candidateCount,
        long deliveredCount,
        long candidateUnsafeCount,
        long candidateInvalidWaypointCount,
        long validatorInterceptionCount,
        long unsafeDeliveredCount,
        long invalidDeliveredWaypointCount
) {
    public SafetyRateCounts {
        if (candidateCount < 0 || deliveredCount < 0
                || candidateUnsafeCount < 0 || candidateInvalidWaypointCount < 0
                || validatorInterceptionCount < 0 || unsafeDeliveredCount < 0
                || invalidDeliveredWaypointCount < 0) {
            throw new IllegalArgumentException("safety counts must be >= 0");
        }
    }

    public static SafetyRateCounts empty() {
        return new SafetyRateCounts(0, 0, 0, 0, 0, 0, 0);
    }

    public SafetyRateCounts plusCandidate(DecisionValidationResult validation) {
        boolean intercepted = validation != null && !validation.valid();
        boolean unsafe = intercepted && ValidationSafetyClassifier.hasUnsafe(validation);
        boolean invalidWaypoint = intercepted && ValidationSafetyClassifier.hasInvalidWaypoint(validation);
        return new SafetyRateCounts(
                candidateCount + 1,
                deliveredCount,
                candidateUnsafeCount + (unsafe ? 1 : 0),
                candidateInvalidWaypointCount + (invalidWaypoint ? 1 : 0),
                validatorInterceptionCount + (intercepted ? 1 : 0),
                unsafeDeliveredCount,
                invalidDeliveredWaypointCount
        );
    }

    public SafetyRateCounts plusDelivered(boolean unsafe, boolean invalidWaypoint) {
        return new SafetyRateCounts(
                candidateCount,
                deliveredCount + 1,
                candidateUnsafeCount,
                candidateInvalidWaypointCount,
                validatorInterceptionCount,
                unsafeDeliveredCount + (unsafe ? 1 : 0),
                invalidDeliveredWaypointCount + (invalidWaypoint ? 1 : 0)
        );
    }

    public SafetyRates rates() {
        return new SafetyRates(
                SafetyRates.ratio(candidateUnsafeCount, candidateCount),
                SafetyRates.ratio(candidateInvalidWaypointCount, candidateCount),
                SafetyRates.ratio(validatorInterceptionCount, candidateCount),
                SafetyRates.ratio(unsafeDeliveredCount, deliveredCount),
                SafetyRates.ratio(invalidDeliveredWaypointCount, deliveredCount)
        );
    }
}
