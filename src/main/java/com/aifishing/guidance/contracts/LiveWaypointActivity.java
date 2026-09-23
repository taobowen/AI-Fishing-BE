package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record LiveWaypointActivity(
        String schemaVersion,
        UUID tripWaypointId,
        Integer radiusMeters,
        int activeAnglersNearby,
        int recentFishOnCount,
        LiveWaypointPressure pressure,
        Instant observedAt
) {
}
