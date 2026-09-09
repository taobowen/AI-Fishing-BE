package com.aifishing.feedback.performance.dto;

import java.util.UUID;

public record WaypointPerformanceDto(
        UUID tripWaypointId,
        int sequence,
        int fishingEffortSeconds,
        int landedCount,
        int lostCount,
        Double rawLandedCpue,
        Double smoothedScore
) {
}
