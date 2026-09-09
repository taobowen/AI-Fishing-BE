package com.aifishing.strategy.context;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.ExperienceLevel;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.enums.PropulsionType;

import java.util.List;
import java.util.Map;

public record UserFishingContext(
        ExperienceLevel experienceLevel,
        List<FishSpecies> preferredSpecies,
        List<String> preferredFishingStyles,
        List<GearSummary> gear,
        BoatSummary boat
) {
    public UserFishingContext {
        preferredSpecies = preferredSpecies == null ? List.of() : List.copyOf(preferredSpecies);
        preferredFishingStyles = preferredFishingStyles == null ? List.of() : List.copyOf(preferredFishingStyles);
        gear = gear == null ? List.of() : List.copyOf(gear);
    }

    public record GearSummary(
            GearType type,
            String name,
            String brand,
            Map<String, Object> metadata
    ) {
    }

    public record BoatSummary(
            String name,
            BoatType type,
            List<PropulsionType> propulsionTypes,
            PropulsionType primaryTransitPropulsionType,
            List<MotorSummary> motors,
            Double maxSpeedKmh
    ) {
        public BoatSummary {
            propulsionTypes = propulsionTypes == null ? List.of() : List.copyOf(propulsionTypes);
            motors = motors == null ? List.of() : List.copyOf(motors);
        }
    }

    public record MotorSummary(
            PropulsionType propulsionType,
            String manufacturer,
            String model,
            Double horsepower,
            Double thrustLb
    ) {
    }
}
