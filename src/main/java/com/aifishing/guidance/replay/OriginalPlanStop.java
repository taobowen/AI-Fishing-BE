package com.aifishing.guidance.replay;

import java.util.UUID;

public record OriginalPlanStop(
        UUID tripWaypointId,
        int sequence,
        Double lat,
        Double lng,
        String featureType
) {
}
