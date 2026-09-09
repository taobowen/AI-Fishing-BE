package com.aifishing.strategy;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.LakeStrategyContext;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.context.TripContext;
import com.aifishing.strategy.context.UserFishingContext;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TargetSpeciesPriority;
import com.aifishing.strategy.domain.TechniquePreference;
import com.aifishing.strategy.domain.WeatherInterpretation;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StrategyFixtures {

    public static final UUID TRIP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private StrategyFixtures() {
    }

    public static FishingStrategyProfile zeroStructureProfile() {
        return new FishingStrategyProfile(
                List.of(new TargetSpeciesPriority(FishSpecies.SMALLMOUTH_BASS, 1)),
                List.of(),
                List.of(new StrategyTimeWindow(
                        LocalTime.of(6, 0),
                        LocalTime.of(10, 0),
                        new DepthRange(2, 4),
                        List.of(),
                        List.of(new TechniquePreference(TechniqueType.NED_RIG, 0.8, "Finesse without structure priors"))
                )),
                List.of(new TechniquePreference(TechniqueType.JERKBAIT, 0.6, "Day-level prior")),
                new WeatherInterpretation("Overcast; no mapped structure after analysis.", 0.4),
                0.5,
                null,
                List.of("No structure features were available after analysis"),
                List.of(new DataLimitation(DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS, "none"))
        );
    }

    public static FishingStrategyProfile validProfile() {
        return new FishingStrategyProfile(
                List.of(new TargetSpeciesPriority(FishSpecies.SMALLMOUTH_BASS, 1)),
                List.of(new StructurePreference(FeatureType.HUMP, 0.9, "Day-level prior for humps")),
                List.of(new StrategyTimeWindow(
                        LocalTime.of(6, 0),
                        LocalTime.of(10, 0),
                        new DepthRange(2, 4),
                        List.of(new StructurePreference(FeatureType.HUMP, 0.85, "Morning humps")),
                        List.of(new TechniquePreference(TechniqueType.NED_RIG, 0.8, "Finesse"))
                )),
                List.of(new TechniquePreference(TechniqueType.JERKBAIT, 0.6, "Day-level prior")),
                new WeatherInterpretation("Overcast with light wind; air temp is not water temp.", 0.7),
                0.88,
                null,
                List.of(),
                List.of()
        );
    }

    public static FishingContext context(
            PipelineReadiness readiness,
            WeatherAvailability weather,
            boolean targetConfirmed,
            int humpCount,
            Double avgConfidence,
            Double maxDepthM
    ) {
        List<LakeStrategyContext.StructureTypeSummary> structure = List.of(
                new LakeStrategyContext.StructureTypeSummary(FeatureType.HUMP, humpCount > 0, humpCount, avgConfidence),
                new LakeStrategyContext.StructureTypeSummary(FeatureType.DROP_OFF, false, 0, null),
                new LakeStrategyContext.StructureTypeSummary(FeatureType.FLAT, false, 0, null),
                new LakeStrategyContext.StructureTypeSummary(FeatureType.POINT, false, 0, null),
                new LakeStrategyContext.StructureTypeSummary(FeatureType.BASIN, false, 0, null),
                new LakeStrategyContext.StructureTypeSummary(FeatureType.ISLAND_EDGE, false, 0, null)
        );
        List<DataLimitation> limitations = new java.util.ArrayList<>();
        if (readiness == PipelineReadiness.FAILED || readiness == PipelineReadiness.NOT_READY) {
            limitations.add(new DataLimitation(DataLimitationCode.STRUCTURE_PIPELINE_NOT_READY));
        }
        if ((readiness == PipelineReadiness.READY || readiness == PipelineReadiness.PARTIAL) && humpCount == 0) {
            limitations.add(new DataLimitation(DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS));
        }
        if (!targetConfirmed) {
            limitations.add(new DataLimitation(DataLimitationCode.TARGET_SPECIES_UNCONFIRMED));
        }
        limitations.add(new DataLimitation(DataLimitationCode.WATER_TEMPERATURE_UNAVAILABLE));
        if (weather == WeatherAvailability.OUT_OF_FORECAST_RANGE) {
            limitations.add(new DataLimitation(DataLimitationCode.WEATHER_OUT_OF_FORECAST_RANGE));
        } else if (weather == WeatherAvailability.FAILED) {
            limitations.add(new DataLimitation(DataLimitationCode.WEATHER_FAILED));
        } else if (weather == WeatherAvailability.UNAVAILABLE) {
            limitations.add(new DataLimitation(DataLimitationCode.WEATHER_UNAVAILABLE));
        }
        LakeStrategyContext lake = new LakeStrategyContext(
                "Head Lake",
                "Ontario",
                null,
                5.0,
                maxDepthM,
                Map.of("BATHYMETRY_LINE", "AVAILABLE", "REGULATION", "AVAILABLE"),
                targetConfirmed ? List.of(FishSpecies.SMALLMOUTH_BASS) : List.of(FishSpecies.WALLEYE),
                List.of(),
                List.of(),
                new LakeStrategyContext.RegulationCoverage("AVAILABLE", 1, List.of("SANCTUARY")),
                Pipeline.GIS,
                readiness,
                "strategy-fixture",
                structure,
                humpCount,
                avgConfidence,
                limitations
        );
        WeatherContext weatherContext = new WeatherContext(
                weather,
                Instant.parse("2026-09-02T12:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                weather == WeatherAvailability.FORECAST_AVAILABLE ? 14.0 : null,
                weather == WeatherAvailability.FORECAST_AVAILABLE ? 12.0 : null,
                270.0,
                0.2,
                80.0,
                1012.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                List.of(),
                "Air temperature is a forecast of air, not observed lake water temperature."
        );
        return new FishingContext(
                new TripContext(
                        TRIP_ID,
                        LocalDate.of(2026, 9, 12),
                        LocalTime.of(6, 0),
                        LocalTime.of(15, 0),
                        "America/Toronto",
                        FishSpecies.SMALLMOUTH_BASS,
                        List.of(FishSpecies.WALLEYE),
                        FishingMode.BOAT,
                        null
                ),
                new UserFishingContext(null, List.of(), List.of(), List.of(), null),
                lake,
                weatherContext,
                limitations
        );
    }
}
