package com.aifishing.planning.environment;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.LakeNavRasterBuilder;
import com.aifishing.planning.spatial.SnapshotWaterPathService;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneSubPlanner;
import com.aifishing.planning.spatial.ZoneVisitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateOrientationCacheTest {

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void sameTargetResolvesOnceAndDifferentTargetsResolveIndependently() {
        LakePlanningGeometry lake = lake();
        CountingOrientationService service = new CountingOrientationService();
        GenerateOrientationCache cache = new GenerateOrientationCache();
        CandidateSpot a = member("a", 0, 0);
        CandidateSpot b = member("b", 80, 0);

        LocalOrientation firstA = cache.getOrResolve(null, a, lake, service).orientation();
        assertThat(cache.getOrResolve(null, a, lake, service).hit()).isTrue();
        assertThat(cache.getOrResolve(null, a, lake, service).hit()).isTrue();
        LocalOrientation firstB = cache.getOrResolve(null, b, lake, service).orientation();
        assertThat(cache.getOrResolve(null, b, lake, service).hit()).isTrue();

        assertThat(service.calls).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(2);
        LocalOrientationService baseline = new LocalOrientationService();
        assertThat(firstA).isEqualTo(baseline.resolve(a, lake));
        assertThat(firstB).isEqualTo(baseline.resolve(b, lake));
        assertThat(cache.contains(null, ZoneVisitState.memberId(a))).isTrue();
        assertThat(cache.contains(null, ZoneVisitState.memberId(b))).isTrue();
        assertThat(service.calls).isEqualTo(2);
    }

    @Test
    void cachedLookupMatchesUncachedResolve() {
        LakePlanningGeometry lake = lake();
        LocalOrientationService service = new LocalOrientationService();
        GenerateOrientationCache cache = new GenerateOrientationCache();
        CandidateSpot spot = member("match", 40, 20);
        LocalOrientation uncached = service.resolve(spot, lake);
        LocalOrientation cached = cache.getOrResolve(null, spot, lake, service).orientation();
        assertThat(cached).isEqualTo(uncached);
        assertThat(cache.getOrResolve(null, spot, lake, service).orientation()).isEqualTo(uncached);
    }

    @Test
    void cachesDoNotLeakAcrossPlanningContexts() {
        LakePlanningGeometry lake = lake();
        CountingOrientationService service = new CountingOrientationService();
        CandidateSpot spot = member("leak", 10, 10);
        PlanningContext first = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20), new PlanningProperties(), RoutePlannerHarness.launch());
        PlanningContext second = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20), new PlanningProperties(), RoutePlannerHarness.launch());

        first.orientationCache().getOrResolve(null, spot, lake, service);
        assertThat(first.orientationCache().contains(null, ZoneVisitState.memberId(spot))).isTrue();
        assertThat(second.orientationCache().contains(null, ZoneVisitState.memberId(spot))).isFalse();
        second.orientationCache().getOrResolve(null, spot, lake, service);
        assertThat(service.calls).isEqualTo(2);
        assertThat(first.orientationCache()).isNotSameAs(second.orientationCache());
    }

    @Test
    void zoneSubPlannerReusesCachedOrientationAndKeepsPackagesIdentical() {
        LakePlanningGeometry lake = lake();
        CountingOrientationService counting = new CountingOrientationService();
        ZoneSubPlanner cachedPlanner = planner(counting);
        PlanningContext context = contextWithLake(lake);
        CandidateSpot close = member("close", 80, 0);
        CandidateSpot far = member("far", 180, 40);
        CandidateSpot zone = zone(List.of(close, far), lake);
        VisitPortal entry = new VisitPortal("in", close.getEntryPoint());
        VisitPortal exit = new VisitPortal("out", close.getExitPoint());
        Instant arrival = Instant.parse("2026-09-21T12:00:00Z");

        GenerateProfiler profiler = GenerateProfiler.begin();
        List<ZoneFishingPackage> first = cachedPlanner.packages(
                zone, ZoneVisitState.empty(), arrival, entry, exit, context,
                TimeIndexedWeather.from(context.weather(), java.time.ZoneId.of("America/Toronto")), 180);
        int afterFirst = counting.calls;
        List<ZoneFishingPackage> second = cachedPlanner.packages(
                zone, ZoneVisitState.empty(), arrival.plusSeconds(900), entry, exit, context,
                TimeIndexedWeather.from(context.weather(), java.time.ZoneId.of("America/Toronto")), 180);
        assertThat(counting.calls).isEqualTo(afterFirst);
        assertThat(afterFirst).isEqualTo(2);
        assertThat(profiler.zoneSubPlanner().orientationCacheMisses()).isEqualTo(2);
        assertThat(profiler.zoneSubPlanner().orientationCacheHits()).isGreaterThan(2);
        assertThat(profiler.zoneSubPlanner().orientationRequests())
                .isEqualTo(profiler.zoneSubPlanner().orientationCacheHits() + profiler.zoneSubPlanner().orientationCacheMisses());
        assertThat((Double) profiler.zoneSubPlanner().toMap().get("orientationCacheHitRate")).isGreaterThan(50.0);

        ZoneSubPlanner uncachedPlanner = planner(new LocalOrientationService());
        PlanningContext other = contextWithLake(lake);
        List<ZoneFishingPackage> uncached = uncachedPlanner.packages(
                zone, ZoneVisitState.empty(), arrival, entry, exit, other,
                TimeIndexedWeather.from(other.weather(), java.time.ZoneId.of("America/Toronto")), 180);
        assertThat(fingerprints(first)).isEqualTo(fingerprints(uncached));
        assertThat(fingerprints(second)).isEqualTo(fingerprints(uncachedPlanner.packages(
                zone, ZoneVisitState.empty(), arrival.plusSeconds(900), entry, exit, other,
                TimeIndexedWeather.from(other.weather(), java.time.ZoneId.of("America/Toronto")), 180)));
    }

    private static List<String> fingerprints(List<ZoneFishingPackage> packages) {
        return packages.stream()
                .map(pkg -> pkg.visitMinutes() + "|" + pkg.sequenceFingerprint() + "|" + pkg.consumedMemberIds())
                .collect(Collectors.toList());
    }

    private static ZoneSubPlanner planner(LocalOrientationService orientation) {
        TimeAdjustedSpotUtility utility = new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty());
        return new ZoneSubPlanner(
                new SpatialUtility(utility),
                utility,
                orientation,
                new LakeNavRasterBuilder(new LocalMetricCrs()),
                new SnapshotWaterPathService(null)
        );
    }

    private static PlanningContext contextWithLake(LakePlanningGeometry lake) {
        PlanningContext base = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20), new PlanningProperties(), RoutePlannerHarness.launch());
        return new PlanningContext(
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
    }

    private static LakePlanningGeometry lake() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 12000);
        return new LakePlanningGeometry(water, List.of());
    }

    private static CandidateSpot zone(List<CandidateSpot> members, LakePlanningGeometry lake) {
        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.nameUUIDFromBytes("orient-zone".getBytes()));
        zone.setZoneId(zone.getFeatureId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setTargetGeometry(lake.water());
        zone.setLocation(members.get(0).getLocation());
        zone.setZoneMembers(members);
        return zone;
    }

    private static CandidateSpot member(String key, double eastM, double northM) {
        Point location = RoutePlannerHarness.point(
                PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(eastM, PlanningFixtures.HEAD_LAT),
                PlanningFixtures.HEAD_LAT + RoutePlannerHarness.metersToLat(northM));
        CandidateSpot spot = new CandidateSpot();
        UUID id = UUID.nameUUIDFromBytes(("orient-" + key).getBytes());
        spot.setFeatureId(id);
        spot.setFishingTargetId(id);
        spot.setType(FeatureType.HUMP);
        spot.setTargetKind(TargetKind.POINT);
        spot.setLocation(location);
        spot.setEntryPoint(location);
        spot.setExitPoint(location);
        spot.setTargetGeometry(location);
        spot.setStrategyWeight(0.5);
        return spot;
    }

    private static final class CountingOrientationService extends LocalOrientationService {
        private int calls;

        @Override
        public LocalOrientation resolve(CandidateSpot spot, LakePlanningGeometry geometry) {
            calls++;
            return super.resolve(spot, geometry);
        }
    }
}
