package com.aifishing.planning.spatial;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.SolarPositionService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneSubPlannerPackagesTest {

    @Test
    void durationBudgetsAreSolvedIndependentlyNotAsPrefixes() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 12000);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        CandidateSpot close = member("close", 80, 0, 0.35);
        CandidateSpot far = member("far", 5000, 0, 0.99);
        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.nameUUIDFromBytes("pkg-zone".getBytes()));
        zone.setZoneId(zone.getFeatureId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setTargetGeometry(water);
        zone.setLocation(close.getLocation());
        zone.setZoneMembers(List.of(close, far));
        VisitPortal entry = new VisitPortal("in", close.getEntryPoint());
        VisitPortal exit = new VisitPortal("out", close.getExitPoint());
        ZoneSubPlanner planner = new ZoneSubPlanner(
                new SpatialUtility(new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty())),
                new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty()),
                new LocalOrientationService(),
                new LakeNavRasterBuilder(new LocalMetricCrs()),
                new SnapshotWaterPathService(null)
        );
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        context = new PlanningContext(
                context.trip(),
                context.lake(),
                context.boat(),
                context.access(),
                lake,
                context.restrictions(),
                context.regulationCoverageStatus(),
                context.weather(),
                context.gearTypes(),
                context.profile(),
                context.strategyRun(),
                context.properties(),
                context.warnings()
        );
        context.properties().getSpatial().setInternalCruiseKmh(3.0);
        List<ZoneFishingPackage> packages = planner.packages(
                zone,
                ZoneVisitState.empty(),
                Instant.parse("2026-09-12T12:00:00Z"),
                entry,
                exit,
                context,
                TimeIndexedWeather.from(context.weather(), java.time.ZoneId.of("America/Toronto")),
                180
        );
        assertThat(packages).isNotEmpty();
        assertThat(packages).isNotEmpty();
        boolean farFitsShort = packages.stream()
                .filter(pkg -> pkg.visitMinutes() <= 45)
                .anyMatch(pkg -> pkg.consumedMemberIds().contains(far.getFeatureId()));
        boolean farFitsLong = packages.stream()
                .filter(pkg -> pkg.visitMinutes() >= 135)
                .anyMatch(pkg -> pkg.consumedMemberIds().contains(far.getFeatureId()));
        assertThat(farFitsShort)
                .as("45-minute budget should not reach the far member")
                .isFalse();
        if (farFitsLong) {
            ZoneFishingPackage shortPkg = packages.stream()
                    .filter(pkg -> pkg.visitMinutes() <= 45)
                    .findFirst()
                    .orElse(packages.get(0));
            ZoneFishingPackage longPkg = packages.stream()
                    .filter(pkg -> pkg.visitMinutes() >= 135)
                    .findFirst()
                    .orElse(packages.get(packages.size() - 1));
            assertThat(shortPkg.sequenceFingerprint()).isNotEqualTo(longPkg.sequenceFingerprint());
        }
        ZoneFishingPackage any = packages.get(0);
        Set<UUID> remainingAfterShort = ZoneVisitState.empty()
                .addEntry(any, Instant.parse("2026-09-12T12:00:00Z"))
                .remainingMembers(zone)
                .stream()
                .map(ZoneVisitState::memberId)
                .collect(Collectors.toSet());
        for (UUID consumed : any.consumedMemberIds()) {
            assertThat(remainingAfterShort).doesNotContain(consumed);
        }
    }

    @Test
    void microsInsideOneCastingPositionShareOneDwell() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 2000);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        CandidateSpot a = member("cast-a", 0, 0, 0.9);
        CandidateSpot b = member("cast-b", 16, 0, 0.4);
        CandidateSpot c = member("cast-c", 21, 8, 0.35);
        CandidateSpot d = member("cast-d", 30, 12, 0.3);
        CandidateSpot far = member("cast-far", 200, 0, 0.2);
        ZoneSubPlan plan = plan(lake, List.of(a, b, c, d, far), a.getEntryPoint());
        assertThat(plan.stops()).hasSize(5);
        assertThat(plan.fishingMinutes()).isEqualTo(40);
        assertThat(plan.stops().stream().filter(stop -> stop.fishingMinutes() > 0)).hasSize(2);
        assertThat(plan.stops().stream().map(ZoneSubPlan.MicroStop::fishingTargetId))
                .contains(a.getFishingTargetId(), b.getFishingTargetId(), c.getFishingTargetId(), d.getFishingTargetId(), far.getFishingTargetId());
        long clusterDwells = plan.stops().stream()
                .filter(stop -> !far.getFishingTargetId().equals(stop.fishingTargetId()))
                .filter(stop -> stop.fishingMinutes() > 0)
                .count();
        assertThat(clusterDwells).isEqualTo(1);
    }

    @Test
    void oppositeShoresDoNotShareACastingDwell() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 4);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        CandidateSpot west = member("shore-west", -15, 0, 0.99);
        CandidateSpot north = member("shore-north", -15, 18, 0.4);
        CandidateSpot east = member("shore-east", 15, 0, 0.5);
        ZoneSubPlan plan = plan(lake, List.of(west, north, east), west.getEntryPoint());
        assertThat(plan.stops()).hasSize(3);
        assertThat(plan.fishingMinutes()).isEqualTo(40);
        assertThat(fishingMinutes(plan, west)).isEqualTo(20);
        assertThat(fishingMinutes(plan, east)).isEqualTo(20);
        assertThat(fishingMinutes(plan, north)).isZero();
    }

    private static ZoneSubPlan plan(LakePlanningGeometry lake, List<CandidateSpot> members, Point portal) {
        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.nameUUIDFromBytes("cast-zone".getBytes()));
        zone.setZoneId(zone.getFeatureId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setTargetGeometry(lake.water());
        zone.setLocation(portal);
        zone.setZoneMembers(members);
        ZoneSubPlanner planner = new ZoneSubPlanner(
                new SpatialUtility(new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty())),
                new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty()),
                new LocalOrientationService(),
                new LakeNavRasterBuilder(new LocalMetricCrs()),
                new SnapshotWaterPathService(null)
        );
        PlanningContext base = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        PlanningContext context = new PlanningContext(
                base.trip(),
                base.lake(),
                base.boat(),
                base.access(),
                lake,
                base.restrictions(),
                base.regulationCoverageStatus(),
                base.weather(),
                base.gearTypes(),
                base.profile(),
                base.strategyRun(),
                base.properties(),
                base.warnings()
        );
        return planner.plan(
                zone,
                Instant.parse("2026-09-12T12:00:00Z"),
                90,
                new VisitPortal("in", portal),
                new VisitPortal("out", portal),
                context,
                TimeIndexedWeather.from(context.weather(), java.time.ZoneId.of("America/Toronto"))
        );
    }

    private static int fishingMinutes(ZoneSubPlan plan, CandidateSpot spot) {
        return plan.stops().stream()
                .filter(stop -> spot.getFishingTargetId().equals(stop.fishingTargetId()))
                .mapToInt(ZoneSubPlan.MicroStop::fishingMinutes)
                .findFirst()
                .orElse(-1);
    }

    private static CandidateSpot member(String key, double eastM, double northM, double weight) {
        Point location = RoutePlannerHarness.point(
                PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(eastM, PlanningFixtures.HEAD_LAT),
                PlanningFixtures.HEAD_LAT + RoutePlannerHarness.metersToLat(northM));
        CandidateSpot spot = new CandidateSpot();
        UUID id = UUID.nameUUIDFromBytes(key.getBytes());
        spot.setFeatureId(id);
        spot.setFishingTargetId(id);
        spot.setType(FeatureType.HUMP);
        spot.setTargetKind(TargetKind.POINT);
        spot.setLocation(location);
        spot.setEntryPoint(location);
        spot.setExitPoint(location);
        spot.setTargetGeometry(location);
        spot.setStrategyWeight(weight);
        spot.setFeatureConfidence(0.8);
        return spot;
    }
}
