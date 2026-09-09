package com.aifishing.launch.api;

import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.geo.GeoPointDto;

import java.util.List;
import java.util.UUID;

public record BoatLaunchResponse(
        UUID id,
        String name,
        GeoPointDto location,
        String accessType,
        String source,
        LaunchVerification verification,
        boolean routable,
        String ownershipType,
        List<String> warnings
) {
}
