package com.aifishing.guidance.replay;

import java.time.Instant;
import java.util.UUID;

public record ActualProgressStop(
        UUID tripWaypointId,
        int sequence,
        String status,
        Instant arrivedAt,
        Instant departedAt,
        Double lat,
        Double lng,
        int accumulatedDwellSeconds
) {
}
