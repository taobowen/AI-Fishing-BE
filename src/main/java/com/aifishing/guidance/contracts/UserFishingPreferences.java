package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.TechniqueType;

import java.util.List;
import java.util.UUID;

public record UserFishingPreferences(
        String schemaVersion,
        UUID userId,
        Boolean avoidLongMoveInWind,
        List<TechniqueType> preferredTechniques,
        List<TechniqueType> dislikedTechniques,
        Double maxMoveMeters,
        Double windConservatism
) {
}
