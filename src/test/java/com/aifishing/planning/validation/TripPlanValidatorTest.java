package com.aifishing.planning.validation;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.filter.RegulationFilter;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripPlanValidatorTest {

    private final TripPlanValidator validator = new TripPlanValidator(new RegulationFilter());

    @Test
    void returnAfterDeadlineFails() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 400)), 8, 20),
                new com.aifishing.planning.PlanningProperties(),
                RoutePlannerHarness.launch());
        Instant start = TripClock.startAt(context);
        Instant end = TripClock.endAt(context);
        PlannedStop stop = stop(start.plus(Duration.ofMinutes(30)), 30);
        RoutePlanner.RouteResult lateReturn = new RoutePlanner.RouteResult(
                List.of(stop),
                false,
                start,
                end.plusSeconds(120),
                TravelEstimate.zero(),
                List.of(),
                0,
                30,
                5,
                0);
        assertThat(validator.validate(List.of(stop), context, lateReturn))
                .isEqualTo("VALIDATION_FAILED: return after deadline");
        RoutePlanner.RouteResult onTime = new RoutePlanner.RouteResult(
                List.of(stop),
                false,
                start,
                end.minusSeconds(60),
                TravelEstimate.zero(),
                List.of(),
                0,
                30,
                5,
                0);
        assertThat(validator.validate(List.of(stop), context, onTime)).isNull();
    }

    @Test
    void waitCapExceededFails() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 400)), 8, 20),
                new com.aifishing.planning.PlanningProperties(),
                RoutePlannerHarness.launch());
        Instant start = TripClock.startAt(context);
        PlannedStop stop = new PlannedStop(
                RoutePlannerHarness.candidate(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8"),
                        -78.92,
                        44.75,
                        0.6,
                        LightPreference.NEUTRAL,
                        FeatureType.HUMP),
                start.plus(Duration.ofMinutes(90)),
                start.plus(Duration.ofMinutes(120)),
                30,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                75,
                "LAUNCH");
        assertThat(validator.validate(List.of(stop), context)).isEqualTo("VALIDATION_FAILED: wait cap exceeded");
    }

    private static PlannedStop stop(Instant arrival, int stayMinutes) {
        return new PlannedStop(
                RoutePlannerHarness.candidate(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9"),
                        -78.92,
                        44.75,
                        0.6,
                        LightPreference.NEUTRAL,
                        FeatureType.HUMP),
                arrival,
                arrival.plus(Duration.ofMinutes(stayMinutes)),
                stayMinutes,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null);
    }
}
