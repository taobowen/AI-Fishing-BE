package com.aifishing.fishingsession.dto;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoPointDto;

import java.time.Instant;
import java.util.UUID;

public record NavigationResponse(
        UUID sessionId,
        FishingSessionStatus sessionStatus,
        WaypointProgressResponse currentWaypoint,
        GeoPointDto lastAcceptedLocation,
        Instant lastAcceptedAt,
        Double distanceM,
        Double bearingDegrees,
        UUID currentTransitLegId
) {
}
