package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DwellPolicyTest {

    @Test
    void pointDwellIsShorterThanFlatOnALongTrip() {
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        var point = RoutePlannerHarness.candidate(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6"),
                -78.92,
                44.75,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.POINT);
        var flat = RoutePlannerHarness.candidate(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb7"),
                -78.92,
                44.75,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.FLAT);
        var pointOptions = DwellPolicy.options(point, 480, schedule);
        var flatOptions = DwellPolicy.options(flat, 480, schedule);
        assertThat(pointOptions).isNotEmpty().allMatch(minutes -> minutes <= 45);
        assertThat(flatOptions).isNotEmpty().allMatch(minutes -> minutes >= 45);
        assertThat(flatOptions.stream().mapToInt(Integer::intValue).max().orElseThrow())
                .isGreaterThan(pointOptions.stream().mapToInt(Integer::intValue).max().orElseThrow());
    }

    @Test
    void pointDwellTableIsFifteenToFortyFive() {
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        var point = RoutePlannerHarness.candidate(
                UUID.randomUUID(), -78.92, 44.75, 0.7, LightPreference.NEUTRAL, FeatureType.POINT);
        point.spot().setTargetKind(com.aifishing.planning.spatial.TargetKind.POINT);
        assertThat(DwellPolicy.options(point, 480, schedule)).containsExactly(15, 20, 30, 45);
    }

    @Test
    void pathDwellTableIsThirtyToNinety() {
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        var path = RoutePlannerHarness.candidate(
                UUID.randomUUID(), -78.92, 44.75, 0.7, LightPreference.NEUTRAL, FeatureType.ISLAND_EDGE);
        path.spot().setTargetKind(com.aifishing.planning.spatial.TargetKind.PATH);
        assertThat(DwellPolicy.options(path, 480, schedule)).containsExactly(30, 45, 60, 90);
    }

    @Test
    void zonePackagesAreNotClippedByMaxSpotMinutes() {
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        var zone = RoutePlannerHarness.candidate(
                UUID.randomUUID(), -78.92, 44.75, 0.7, LightPreference.NEUTRAL, FeatureType.FLAT);
        zone.spot().setTargetKind(com.aifishing.planning.spatial.TargetKind.ZONE);
        assertThat(DwellPolicy.options(zone, 480, schedule)).contains(45, 90, 135, 180);
        assertThat(DwellPolicy.options(zone, 480, schedule)).allMatch(minutes -> minutes <= schedule.getMaxZoneVisitMinutes());
    }

    @Test
    void shortRemainingTimeClampsDwellInsideTheWindow() {
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        var spot = RoutePlannerHarness.candidate(
                UUID.randomUUID(),
                -78.92,
                44.75,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        assertThat(DwellPolicy.options(spot, 50, schedule)).allMatch(minutes -> minutes <= 50);
    }
}
