package com.aifishing.trip.api;

import com.aifishing.common.geo.GeoPointDto;

import java.util.UUID;

public record RequiredPointResponse(
        UUID id,
        GeoPointDto location,
        String label,
        int sortOrder
) {
}
