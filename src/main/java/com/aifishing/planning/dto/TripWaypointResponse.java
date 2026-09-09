package com.aifishing.planning.dto;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.tactics.TacticalRecommendation;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TripWaypointResponse(
        UUID id,
        int sequence,
        GeoPointDto location,
        UUID lakeFeatureId,
        FeatureType featureType,
        LocalTime plannedArrivalTime,
        LocalTime plannedDepartureTime,
        Instant plannedArrivalAt,
        Instant plannedDepartureAt,
        Integer plannedDwellMinutes,
        Integer plannedVisitMinutes,
        Integer plannedFishingMinutes,
        Integer plannedInternalTransitMinutes,
        Integer plannedWaitMinutes,
        TargetKind targetKind,
        GeoJsonGeometryDto targetGeometry,
        GeoJsonGeometryDto fishingCorridor,
        GeoPointDto entryPoint,
        GeoPointDto exitPoint,
        GeoJsonGeometryDto selectedFishingPath,
        Double fishingCorridorWidthM,
        UUID zoneId,
        UUID fishingTargetId,
        UUID visitScopeId,
        List<String> visitScopeMemberIds,
        GeoJsonGeometryDto visitEnvelope,
        Boolean closedLoop,
        String traversalKey,
        Double representativeDepthM,
        Double minDepthM,
        Double maxDepthM,
        Double candidateScore,
        ScoreBreakdown scoreBreakdown,
        List<String> recommendedTechniques,
        Double estimatedTravelDistanceFromPreviousM,
        Double estimatedTravelMinutesFromPrevious,
        String reason,
        List<String> whyThisTime,
        Map<String, Object> environment,
        Map<String, Object> metadata,
        List<TripStopSubtargetResponse> subtargets,
        TacticalRecommendation tactical
) {
}
