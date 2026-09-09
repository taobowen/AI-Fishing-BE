package com.aifishing.launch.api;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.common.geo.GeoPointDto;

import java.util.List;
import java.util.UUID;

public record TripLaunchSelectionResponse(
        LaunchSelectionMode mode,
        UUID officialAccessPointId,
        String officialName,
        String officialSource,
        LaunchVerification verification,
        GeoPointDto requestedPoint,
        GeoPointDto shoreAccessPoint,
        GeoPointDto routeStartPoint,
        Double snapDistanceM,
        CustomAccessType customAccessType,
        ShorelineKind shorelineKind,
        String resolutionVersion,
        List<String> warnings
) {
}
