package com.aifishing.lake.api;

import com.aifishing.common.geo.GeoPointDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LakeSummaryResponse(
        UUID id,
        String name,
        String province,
        String country,
        String source,
        GeoPointDto centroid,
        BigDecimal meanDepthM,
        BigDecimal maxDepthM,
        String timeZoneId,
        Instant createdAt,
        Instant updatedAt
) {
}
