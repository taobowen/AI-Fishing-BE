package com.aifishing.fishingprofile.api;

import com.aifishing.common.enums.ExperienceLevel;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.geo.GeoPointDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishingProfileResponse(
        UUID id,
        UUID userId,
        ExperienceLevel experienceLevel,
        List<FishSpecies> preferredSpecies,
        List<String> preferredFishingStyles,
        String homeAddress,
        String homeCity,
        String homeRegion,
        String homeCountry,
        GeoPointDto homeLocation,
        Instant createdAt,
        Instant updatedAt
) {
}
