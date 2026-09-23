package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;

public record GetNearbyWaypointsParams(
        double latitudeWgs84,
        double longitudeWgs84,
        int radiusMeters,
        FishSpecies targetSpecies
) {
}
