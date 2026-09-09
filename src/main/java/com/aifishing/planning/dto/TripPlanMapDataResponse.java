package com.aifishing.planning.dto;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.spatial.TargetKind;

import java.util.List;
import java.util.UUID;

public record TripPlanMapDataResponse(
        UUID planId,
        UUID tripId,
        int version,
        List<MapWaypoint> waypoints
) {
    public record MapWaypoint(
            int sequence,
            GeoPointDto location,
            UUID featureId,
            FeatureType featureType,
            TargetKind targetKind,
            GeoJsonGeometryDto targetGeometry,
            GeoJsonGeometryDto fishingCorridor,
            GeoPointDto entryPoint,
            GeoJsonGeometryDto visitEnvelope
    ) {
    }
}
