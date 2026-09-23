package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.lake.processing.dto.FeatureType;

import java.time.Instant;
import java.util.UUID;

public record HistoricalPerformance(
        String schemaVersion,
        UUID lakeId,
        UUID zoneId,
        UUID tripWaypointId,
        FishSpecies species,
        SeasonBucket seasonBucket,
        TimeBucket timeBucket,
        FeatureType structure,
        WindBucket windBucket,
        WindDirectionBucket windDirectionBucket,
        LureFamily lureFamily,
        Double effortMinutes,
        Integer catchCount,
        Double cpue,
        Double smoothedScore,
        Integer sampleSize,
        Double sampleConfidence,
        Instant updatedAt,
        Long fishingEffortSeconds,
        Integer biteCount,
        Integer fishOnCount,
        Integer landedCount,
        Integer contributingSessionWaypointCount,
        Double biteRate,
        Double fishOnRate,
        Double landingRate,
        Integer empiricalAlgorithmVersion
) {
    public HistoricalPerformance {
        Integer resolvedLanded = alias(landedCount, catchCount, "landedCount", "catchCount");
        landedCount = resolvedLanded;
        catchCount = resolvedLanded;
        Integer resolvedWaypoints = alias(
                contributingSessionWaypointCount,
                sampleSize,
                "contributingSessionWaypointCount",
                "sampleSize"
        );
        contributingSessionWaypointCount = resolvedWaypoints;
        sampleSize = resolvedWaypoints;
    }

    private static Integer alias(Integer preferred, Integer legacy, String preferredName, String legacyName) {
        if (preferred != null && legacy != null && !preferred.equals(legacy)) {
            throw new IllegalArgumentException(preferredName + " must equal " + legacyName);
        }
        return preferred != null ? preferred : legacy;
    }
}
