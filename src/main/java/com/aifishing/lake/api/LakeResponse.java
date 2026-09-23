package com.aifishing.lake.api;

import com.aifishing.common.geo.GeoMultiPolygonDto;
import com.aifishing.common.geo.GeoPointDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LakeResponse(
        UUID id,
        String name,
        String province,
        String country,
        String source,
        String sourceLakeId,
        GeoPointDto centroid,
        GeoMultiPolygonDto boundary,
        BigDecimal meanDepthM,
        BigDecimal maxDepthM,
        String timeZoneId,
        String cardImageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
