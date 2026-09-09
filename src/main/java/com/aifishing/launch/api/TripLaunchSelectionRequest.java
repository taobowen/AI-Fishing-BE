package com.aifishing.launch.api;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.geo.GeoPointDto;
import jakarta.validation.Valid;

import java.util.UUID;

public record TripLaunchSelectionRequest(
        LaunchSelectionMode mode,
        UUID officialAccessPointId,
        @Valid GeoPointDto requestedPoint,
        CustomAccessType customAccessType
) {
}
