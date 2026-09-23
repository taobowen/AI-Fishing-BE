package com.aifishing.strategy.service;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TargetSpeciesPriority;
import com.aifishing.strategy.domain.TechniquePreference;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyProfileValidatorTest {

    private final StrategyProfileValidator validator = new StrategyProfileValidator();
    private final FishingContext context = StrategyFixtures.context(
            PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 3, 0.8, 12.0);

    @Test
    void validProfilePasses() {
        assertThat(validator.validate(StrategyFixtures.validProfile(), context)).isEmpty();
    }

    @Test
    void rejectsWeightOutsideUnitInterval() {
        FishingStrategyProfile profile = withGlobalStructure(new StructurePreference(FeatureType.HUMP, 1.2, "too high"));
        assertThat(validator.validate(profile, context)).anyMatch(error -> error.contains("weight"));
    }

    @Test
    void rejectsDepthMinGreaterThanMax() {
        FishingStrategyProfile profile = withWindow(new StrategyTimeWindow(
                LocalTime.of(6, 0), LocalTime.of(10, 0), new DepthRange(8, 2), List.of(), List.of()));
        assertThat(validator.validate(profile, context)).anyMatch(error -> error.contains("min must be <= max"));
    }

    @Test
    void rejectsDepthAboveLakeMax() {
        FishingStrategyProfile profile = withWindow(new StrategyTimeWindow(
                LocalTime.of(6, 0), LocalTime.of(10, 0), new DepthRange(1, 20), List.of(), List.of()));
        assertThat(validator.validate(profile, context)).anyMatch(error -> error.contains("maxDepthM"));
    }

    @Test
    void rejectsOverlappingWindowsAndWindowsOutsideTripHours() {
        FishingStrategyProfile overlap = withWindows(List.of(
                new StrategyTimeWindow(LocalTime.of(6, 0), LocalTime.of(10, 0), new DepthRange(2, 4), List.of(), List.of()),
                new StrategyTimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0), new DepthRange(2, 4), List.of(), List.of())
        ));
        assertThat(validator.validate(overlap, context)).anyMatch(error -> error.contains("overlaps"));

        FishingStrategyProfile outside = withWindow(new StrategyTimeWindow(
                LocalTime.of(5, 0), LocalTime.of(16, 0), new DepthRange(2, 4), List.of(), List.of()));
        assertThat(validator.validate(outside, context))
                .anyMatch(error -> error.contains("fishingStartTime") || error.contains("fishingEndTime"));
    }

    @Test
    void overnightTripContainsEarlyMorningWindow() {
        FishingContext overnight = new FishingContext(
                new com.aifishing.strategy.context.TripContext(
                        StrategyFixtures.TRIP_ID,
                        LocalDate.of(2026, 9, 18),
                        LocalDate.of(2026, 9, 19),
                        LocalTime.of(20, 0),
                        LocalTime.of(5, 0),
                        "America/Toronto",
                        FishSpecies.SMALLMOUTH_BASS,
                        List.of(FishSpecies.WALLEYE),
                        com.aifishing.common.enums.FishingMode.BOAT,
                        null
                ),
                context.user(),
                context.lake(),
                context.weather(),
                context.dataLimitations()
        );
        FishingStrategyProfile morning = withWindow(new StrategyTimeWindow(
                LocalTime.of(2, 0), LocalTime.of(4, 0), new DepthRange(2, 4), List.of(), List.of()));
        assertThat(validator.validate(morning, overnight)).isEmpty();
    }

    @Test
    void rejectsUnknownEnumViaNullType() {
        FishingStrategyProfile profile = withGlobalStructure(new StructurePreference(null, 0.5, "unknown"));
        assertThat(validator.validate(profile, context)).anyMatch(error -> error.contains("type"));
    }

    @Test
    void rejectsUnavailableStructureWithStrongWeightUnlessWarned() {
        FishingStrategyProfile strong = withGlobalStructure(new StructurePreference(FeatureType.BASIN, 0.9, "invented"));
        assertThat(validator.validate(strong, context)).anyMatch(error -> error.contains("unavailable structure"));

        FishingStrategyProfile warned = new FishingStrategyProfile(
                List.of(new TargetSpeciesPriority(FishSpecies.SMALLMOUTH_BASS, 1)),
                List.of(new StructurePreference(FeatureType.BASIN, 0.9, "fallback only")),
                List.of(),
                List.of(),
                StrategyFixtures.validProfile().weatherInterpretation(),
                0.5,
                null,
                List.of("BASIN is unavailable in the pipeline summary"),
                List.of(new DataLimitation(DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS, "none"))
        );
        assertThat(validator.validate(warned, context)).isEmpty();
    }

    @Test
    void rejectsCoordinatesAndFeatureIdsInRationales() {
        FishingStrategyProfile coords = withGlobalStructure(new StructurePreference(FeatureType.HUMP, 0.4, "go to 44.750, -78.920"));
        assertThat(validator.validate(coords, context)).anyMatch(error -> error.contains("coordinates"));

        FishingStrategyProfile ids = withGlobalStructure(new StructurePreference(FeatureType.HUMP, 0.4, "use featureId abc"));
        assertThat(validator.validate(ids, context)).anyMatch(error -> error.contains("feature IDs"));
    }

    @Test
    void structurePreferencesMustBeArraysNotMaps() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertThatThrownBy(() -> mapper.readValue("""
                {
                  "targetSpecies": [],
                  "structurePreferences": {"HUMP": 0.9},
                  "timeWindows": [],
                  "generalTechniques": [],
                  "warnings": [],
                  "dataLimitations": []
                }
                """, FishingStrategyProfile.class)).isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void missingLightPreferenceDefaultsToNeutral() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        StrategyTimeWindow window = mapper.readValue("""
                {
                  "from": "06:00:00",
                  "to": "10:00:00",
                  "preferredDepthM": {"min": 2, "max": 4},
                  "structurePreferences": [],
                  "techniques": []
                }
                """, StrategyTimeWindow.class);
        assertThat(window.lightPreference()).isEqualTo(com.aifishing.strategy.domain.LightPreference.NEUTRAL);
        assertThat(validator.validate(withWindow(window), context)).isEmpty();
    }

    @Test
    void waterTemperatureAssertionIsWarningNotHardFailure() {
        FishingStrategyProfile profile = new FishingStrategyProfile(
                StrategyFixtures.validProfile().targetSpecies(),
                StrategyFixtures.validProfile().structurePreferences(),
                StrategyFixtures.validProfile().timeWindows(),
                StrategyFixtures.validProfile().generalTechniques(),
                new com.aifishing.strategy.domain.WeatherInterpretation("Water temperature is 14C so fish are shallow", 0.4),
                0.5,
                null,
                List.of(),
                List.of()
        );
        assertThat(validator.validate(profile, context)).isEmpty();
        assertThat(validator.warningsForWaterTemperature(profile, context)).isNotEmpty();
    }

    private FishingStrategyProfile withGlobalStructure(StructurePreference preference) {
        FishingStrategyProfile base = StrategyFixtures.validProfile();
        return new FishingStrategyProfile(
                base.targetSpecies(),
                List.of(preference),
                base.timeWindows(),
                base.generalTechniques(),
                base.weatherInterpretation(),
                base.modelConfidence(),
                null,
                base.warnings(),
                base.dataLimitations()
        );
    }

    private FishingStrategyProfile withWindow(StrategyTimeWindow window) {
        return withWindows(List.of(window));
    }

    private FishingStrategyProfile withWindows(List<StrategyTimeWindow> windows) {
        FishingStrategyProfile base = StrategyFixtures.validProfile();
        return new FishingStrategyProfile(
                base.targetSpecies(),
                base.structurePreferences(),
                windows,
                base.generalTechniques(),
                base.weatherInterpretation(),
                base.modelConfidence(),
                null,
                base.warnings(),
                base.dataLimitations()
        );
    }
}
