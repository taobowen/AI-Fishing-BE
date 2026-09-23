package com.aifishing.guidance.empirical;

public record EmpiricalRawCounts(
        long fishingEffortSeconds,
        int biteCount,
        int fishOnCount,
        int landedCount,
        int contributingSessionWaypointCount
) {

    public static final EmpiricalRawCounts ZERO = new EmpiricalRawCounts(0, 0, 0, 0, 0);

    public EmpiricalRawCounts plus(EmpiricalRawCounts other) {
        if (other == null) {
            return this;
        }
        return new EmpiricalRawCounts(
                fishingEffortSeconds + other.fishingEffortSeconds,
                biteCount + other.biteCount,
                fishOnCount + other.fishOnCount,
                landedCount + other.landedCount,
                contributingSessionWaypointCount + other.contributingSessionWaypointCount
        );
    }

    public boolean isEmpty() {
        return fishingEffortSeconds == 0
                && biteCount == 0
                && fishOnCount == 0
                && landedCount == 0
                && contributingSessionWaypointCount == 0;
    }
}
