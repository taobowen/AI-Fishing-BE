package com.aifishing.lake.ingestion.admin;

import java.util.List;
import java.util.UUID;

public record LakeBootstrapValidationResponse(
        UUID lakeId,
        String lakeName,
        Long ogfId,
        String officialName,
        String timeZoneId,
        boolean boundaryPresent,
        Double lakeAreaM2,
        String postgisVersion,
        boolean postgisAvailable,
        LakeDataSummaryResponse summary,
        GeometrySanityReport geometry,
        List<String> mappedSpecies,
        boolean empiricalColdStart,
        long catchEventCount,
        long fishingEffortSegmentCount,
        List<String> warnings
) {
    public record GeometrySanityReport(
            int featureCount,
            int invalidGeometryCount,
            int wrongSridCount,
            int outsideBoundaryCount,
            int confidenceOutOfRangeCount,
            int polygonAbsurdAreaCount,
            int lineAbsurdLengthCount,
            int pointGeometriesSkippedForArea,
            int exactDuplicateGroupCount,
            int exactDuplicateFeatureCount,
            List<String> warnings
    ) {
    }
}
