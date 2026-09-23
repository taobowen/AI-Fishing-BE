package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record GuidancePlanStep(
        String schemaVersion,
        int step,
        GuidanceAction type,
        boolean committed,
        UUID tripWaypointId,
        Integer durationMinutes
) {
}
