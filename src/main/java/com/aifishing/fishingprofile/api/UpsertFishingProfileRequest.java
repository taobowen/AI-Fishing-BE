package com.aifishing.fishingprofile.api;

import com.aifishing.common.enums.ExperienceLevel;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.geo.GeoPointDto;
import jakarta.validation.Valid;

import java.util.List;

public record UpsertFishingProfileRequest(
        ExperienceLevel experienceLevel,
        List<FishSpecies> preferredSpecies,
        List<String> preferredFishingStyles,
        String homeAddress,
        String homeCity,
        String homeRegion,
        String homeCountry,
        @Valid GeoPointDto homeLocation
) {
}
