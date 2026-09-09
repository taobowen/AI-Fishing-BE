package com.aifishing.launch.api;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.common.geo.GeoPointDto;

import java.util.List;
import java.util.UUID;

public record CustomLaunchPreviewResponse(
        GeoPointDto requestedPoint,
        GeoPointDto shoreAccessPoint,
        GeoPointDto routeStartPoint,
        Double snapDistanceM,
        ShorelineKind shorelineKind,
        CustomAccessType customAccessType,
        String resolutionVersion,
        List<String> warnings,
        List<NearbyOfficialSuggestion> nearbyOfficial
) {
    public record NearbyOfficialSuggestion(
            UUID id,
            String name,
            GeoPointDto location,
            String accessType,
            String source,
            LaunchVerification verification,
            Double distanceM
    ) {
    }
}
