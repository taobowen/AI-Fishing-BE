package com.aifishing.fishingsession.dto;

import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.domain.WaypointSkipReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WaypointProgressResponse(
        UUID id,
        UUID tripWaypointId,
        int sequence,
        WaypointProgressStatus status,
        WaypointSkipReason skipReason,
        GeoPointDto location,
        Instant firstApproachedAt,
        Instant arrivedAt,
        Instant departedAt,
        Instant completedAt,
        Instant skippedAt,
        int accumulatedDwellSeconds,
        Double closestDistanceM,
        List<String> recommendedTechniques
) {
}
