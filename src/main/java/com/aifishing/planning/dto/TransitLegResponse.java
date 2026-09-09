package com.aifishing.planning.dto;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.planning.spatial.TransitEndpointKind;

import java.util.UUID;

public record TransitLegResponse(
        UUID id,
        int sequence,
        TransitEndpointKind fromKind,
        TransitEndpointKind toKind,
        UUID fromVisitId,
        UUID toVisitId,
        GeoJsonGeometryDto transitPath,
        Double pathDistanceMeters,
        Double plannedTravelMinutes,
        UUID sourceWaterPathId,
        String navigationVersion
) {
}
