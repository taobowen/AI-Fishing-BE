package com.aifishing.planning.route;

import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.AccessibilityFilter;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.filter.SafetyFilter;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredPointConstraintValidatorTest {

    private final AccessibilityFilter accessibilityFilter = new AccessibilityFilter(
            (LakeAccessPointRepository) null,
            (LakeWaterwayRepository) null
    );
    private final RequiredPointConstraintValidator validator = new RequiredPointConstraintValidator(
            accessibilityFilter,
            new BoatCapabilityFilter(),
            new SafetyFilter()
    );

    @Test
    void singleAccessFailureIsRequiredPointInfeasible() {
        PlanningProperties properties = new PlanningProperties();
        PlanningContext context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        CandidateSpot outside = point(PlanningFixtures.HEAD_LNG + 1.0, PlanningFixtures.HEAD_LAT + 1.0);
        assertThatThrownBy(() -> validator.validate(List.of(outside), context))
                .isInstanceOf(RequiredPointConstraintValidator.RequiredPointInfeasibleException.class)
                .satisfies(ex -> {
                    var failure = (RequiredPointConstraintValidator.RequiredPointInfeasibleException) ex;
                    assertThat(failure.errorCode()).isEqualTo(RoutePlanConstraints.REQUIRED_POINT_INFEASIBLE);
                    assertThat(failure.pointReason()).isEqualTo("ACCESS");
                    assertThat(ex.getMessage()).startsWith(RoutePlanConstraints.REQUIRED_POINT_INFEASIBLE + ":ACCESS");
                });
    }

    @Test
    void singleRangeFailureIsRequiredPointInfeasible() {
        PlanningProperties properties = new PlanningProperties();
        properties.getTravel().setMaxOneWayFractionOfTrip(0.01);
        properties.getTravel().setDefaultBoatKmh(5.0);
        PlanningContext context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        // Inside Head Lake water (~1.2 km square) but beyond the tiny travel cap.
        CandidateSpot far = point(
                PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(900, PlanningFixtures.HEAD_LAT),
                PlanningFixtures.HEAD_LAT);
        assertThatThrownBy(() -> validator.validate(List.of(far), context))
                .isInstanceOf(RequiredPointConstraintValidator.RequiredPointInfeasibleException.class)
                .satisfies(ex -> {
                    var failure = (RequiredPointConstraintValidator.RequiredPointInfeasibleException) ex;
                    assertThat(failure.errorCode()).isEqualTo(RoutePlanConstraints.REQUIRED_POINT_INFEASIBLE);
                    assertThat(failure.pointReason()).isEqualTo("RANGE");
                });
    }

    @Test
    void mapsBoatTravelToRange() {
        assertThat(RequiredPointConstraintValidator.mapReason(
                RejectionReason.BOAT_TRAVEL_UNREASONABLE, new BoatCapabilityFilter()))
                .isEqualTo("RANGE");
        assertThat(RequiredPointConstraintValidator.mapReason(
                RejectionReason.SAFETY_HARD_REJECT, new SafetyFilter()))
                .isEqualTo("WEATHER");
        assertThat(RequiredPointConstraintValidator.mapReason(
                RejectionReason.OUTSIDE_LAKE, accessibilityFilter))
                .isEqualTo("ACCESS");
    }

    private static CandidateSpot point(double lng, double lat) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setFishingTargetId(UUID.randomUUID());
        spot.setType(FeatureType.POINT);
        spot.setTargetKind(TargetKind.POINT);
        spot.setLocation(RoutePlannerHarness.point(lng, lat));
        spot.setEntryPoint(spot.getLocation());
        spot.setExitPoint(spot.getLocation());
        spot.setTargetGeometry(spot.getLocation());
        return spot;
    }

    private static WeatherContext calm() {
        return new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                16.0,
                8.0,
                270.0,
                0.0,
                20.0,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 600)),
                "air"
        );
    }
}
