package com.aifishing.fishingsession.dto;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.planning.dto.TransitLegResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FishingSessionResponse(
        UUID id,
        UUID tripId,
        UUID userId,
        UUID tripPlanId,
        Integer planVersion,
        FishingSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant pausedAt,
        int totalPausedSeconds,
        Map<String, Object> summary,
        List<WaypointProgressResponse> waypoints,
        List<TransitLegResponse> transitLegs,
        boolean returningToLaunch,
        GeoPointDto launchPoint,
        UUID currentTransitLegId
) {
}
