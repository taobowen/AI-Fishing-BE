package com.aifishing.guidance.metrics;

/**
 * Candidate vs delivered safety raw counts. Workstream B persists and derives
 * rates; classification of unsafe/invalid rows is owned by reliability gates.
 */
public record OnlineOutcomeSafetyCounts(
        long candidateCount,
        long deliveredCount,
        long candidateUnsafeCount,
        long candidateInvalidWaypointCount,
        long validatorInterceptionCount,
        long unsafeDeliveredCount,
        long invalidDeliveredWaypointCount
) {
    public static OnlineOutcomeSafetyCounts empty() {
        return new OnlineOutcomeSafetyCounts(0, 0, 0, 0, 0, 0, 0);
    }
}
