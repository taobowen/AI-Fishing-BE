package com.aifishing.trip.api;

import com.aifishing.common.geo.GeoPointDto;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Conservative one-shot envelope for map gray-out on Required Points.
 * When {@code applyRangeCap} is false, the client must skip boat-range gray-out
 * ({@code oneWayCapKm} is null). Shore mode and unknown launch/boat always skip.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RequiredPointReachabilityResponse(
        GeoPointDto routeStart,
        Double oneWayCapKm,
        boolean applyRangeCap,
        String disclaimer
) {
}
