package com.aifishing.feedback.performance.dto;

import java.util.List;
import java.util.UUID;

public record SessionPerformanceResponse(
        UUID sessionId,
        int fishingEffortSeconds,
        int travelSeconds,
        int unknownSeconds,
        int pausedSeconds,
        int landedCount,
        int lostCount,
        int pendingCount,
        Double rawLandedCpue,
        UUID bestWaypointId,
        Double bestWaypointSmoothedScore,
        List<WaypointPerformanceDto> waypoints
) {
}
