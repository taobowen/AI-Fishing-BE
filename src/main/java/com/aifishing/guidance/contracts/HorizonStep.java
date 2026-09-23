package com.aifishing.guidance.contracts;

import java.util.UUID;

public record HorizonStep(
        int step,
        GuidanceAction type,
        UUID tripWaypointId,
        Integer durationMinutes,
        boolean committed
) {
}
