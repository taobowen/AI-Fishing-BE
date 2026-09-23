package com.aifishing.guidance.contracts;

import java.util.UUID;

public record GetAlternativeRouteParams(
        double fromLatitudeWgs84,
        double fromLongitudeWgs84,
        UUID toTripWaypointId,
        Double remainingRangeMeters
) {
}
