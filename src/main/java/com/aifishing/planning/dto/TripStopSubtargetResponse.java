package com.aifishing.planning.dto;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.tactics.TacticalRecommendation;

import java.time.Instant;
import java.util.UUID;

public record TripStopSubtargetResponse(
        UUID id,
        int sequence,
        TargetKind targetKind,
        UUID fishingTargetId,
        GeoJsonGeometryDto geometry,
        GeoPointDto entryPoint,
        GeoPointDto exitPoint,
        Instant plannedArrivalAt,
        Instant plannedDepartureAt,
        Integer plannedFishingMinutes,
        Integer plannedInternalTransitMinutes,
        Double score,
        String reason,
        TacticalRecommendation tactical
) {
}
