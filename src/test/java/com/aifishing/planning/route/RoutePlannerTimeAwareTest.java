package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePlannerTimeAwareTest {

    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @Test
    void waitsAtLaunchInsteadOfFillerWhenLaterWindowIsMuchBetter() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setMinWaypoints(1);
        properties.getSchedule().setWaitPenalty(0.02);
        properties.getSchedule().setMaxTotalWaitMinutes(30);
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSchedule().setMaxSpotMinutes(20);
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 45, 80, 100),
                RoutePlannerHarness.hour(8, 15, 45, 80, 100),
                RoutePlannerHarness.hour(8, 30, 8, 20, 600),
                RoutePlannerHarness.hour(9, 0, 8, 20, 600)
        ), 45, 80);
        PlanningContext context = RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID prizeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID fillerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        var prize = RoutePlannerHarness.candidate(
                prizeId, lng, lat, 0.80, LightPreference.NEUTRAL, FeatureType.HUMP);
        var filler = RoutePlannerHarness.candidate(
                fillerId,
                lng + RoutePlannerHarness.metersToLng(80, lat),
                lat,
                0.40,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        RoutePlanner.RouteResult result = planner.plan(List.of(prize, filler), context);
        assertThat(result.stops()).hasSize(1);
        assertThat(result.stops().get(0).candidate().spot().getFeatureId()).isEqualTo(prizeId);
        assertThat(result.totalWaitMinutes()).isGreaterThanOrEqualTo(15);
        assertThat(result.waitEvents()).isNotEmpty();
    }

    @Test
    void morningBestOverridesHigherIntrinsicAfternoonSpot() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setMaxWaypoints(2);
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSchedule().setMaxSpotMinutes(20);
        properties.getEnvironment().getSolar().setMaxWeight(0.40);
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 10, 700),
                RoutePlannerHarness.hour(12, 0, 8, 10, 750),
                RoutePlannerHarness.hour(16, 0, 8, 10, 650)
        ), 8, 10);
        PlanningContext context = RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID afternoonId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
        UUID morningId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4");
        var afternoon = RoutePlannerHarness.candidate(
                afternoonId,
                lng + RoutePlannerHarness.metersToLng(1100, lat),
                lat,
                0.55,
                LightPreference.SHADE_PREFERRED,
                FeatureType.POINT);
        var morning = RoutePlannerHarness.candidate(
                morningId,
                lng - RoutePlannerHarness.metersToLng(1100, lat),
                lat,
                0.52,
                LightPreference.SUN_EXPOSED,
                FeatureType.POINT);
        RoutePlanner.RouteResult result = planner.plan(List.of(afternoon, morning), context);
        assertThat(result.stops()).isNotEmpty();
        assertThat(result.stops().get(0).candidate().spot().getFeatureId()).isEqualTo(morningId);
    }

    @Test
    void stormyTransitIsRejectedEvenWhenDestinationHourIsCalm() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setWaitOptionsMinutes(List.of());
        properties.getSchedule().setMaxTotalWaitMinutes(0);
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 45, 80, 100),
                RoutePlannerHarness.hour(9, 0, 8, 20, 600)
        ), 45, 80);
        PlanningContext context = RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        var far = RoutePlannerHarness.candidate(
                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc5"),
                lng + RoutePlannerHarness.metersToLng(900, lat),
                lat + RoutePlannerHarness.metersToLat(900),
                0.9,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        RoutePlanner.RouteResult result = planner.plan(List.of(far), context);
        assertThat(result.stops()).isEmpty();
    }

    @Test
    void dwellAndReturnStayInsideTripEnd() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setMaxWaypoints(3);
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 30, 400),
                RoutePlannerHarness.hour(16, 0, 8, 30, 200)
        ), 8, 30);
        PlanningContext context = RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
        var spot = RoutePlannerHarness.candidate(
                UUID.randomUUID(),
                PlanningFixtures.HEAD_LNG,
                PlanningFixtures.HEAD_LAT,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        RoutePlanner.RouteResult result = planner.plan(List.of(spot), context);
        assertThat(result.stops()).hasSize(1);
        assertThat(result.plannedReturnAt()).isBeforeOrEqualTo(
                com.aifishing.planning.environment.TripClock.endAt(context));
        assertThat(result.stops().get(0).departureAt())
                .isBeforeOrEqualTo(result.plannedReturnAt());
    }
}
