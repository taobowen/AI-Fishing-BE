package com.aifishing.fishingsession.dto;

import com.aifishing.common.geo.GeoPointDto;

import java.time.Instant;
import java.util.UUID;

public record AdHocFishingStopResponse(
        UUID id,
        Instant startedAt,
        Instant endedAt,
        GeoPointDto location,
        UUID fishingTargetId,
        UUID physicalZoneId,
        UUID lakeFeatureId
) {
}
