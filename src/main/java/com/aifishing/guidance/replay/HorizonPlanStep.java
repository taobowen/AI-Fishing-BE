package com.aifishing.guidance.replay;

import com.aifishing.guidance.contracts.GuidanceAction;

import java.util.UUID;

public record HorizonPlanStep(
        int step,
        GuidanceAction type,
        UUID tripWaypointId,
        Double lat,
        Double lng,
        Integer durationMinutes,
        boolean committed
) {
}
