package com.aifishing.planning.spatial;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateLocationService;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Phase891ArchitectureTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void A_atomicTargetKindsNeverContainZone() {
        assertThat(TargetKind.atomicTargetKinds()).containsExactly(TargetKind.POINT, TargetKind.PATH);
        assertThat(TargetKind.atomicTargetKinds()).doesNotContain(TargetKind.ZONE);
        assertThat(TargetKind.ZONE.allowedOnLakeFishingTargets()).isFalse();
    }

    @Test
    void B_fastBoatOneScopeSlowBoatSeveral() {
        CandidateSpot zone = linearZone(8, 90);
        ZoneVisitScopeDeriver deriver = deriver();
        var fastCtx = contextWithBoat(22);
        var slowCtx = contextWithBoat(3.2);
        List<CandidateSpot> fast = deriver.derive(zone, fastCtx);
        List<CandidateSpot> slow = deriver.derive(copyZone(zone), slowCtx);
        assertThat(fast).hasSize(1);
        assertThat(fast.get(0).getZoneMembers()).hasSize(8);
        assertThat(slow.size()).isGreaterThan(1);
        assertThat(slow.stream().mapToInt(s -> s.getZoneMembers().size()).max().orElse(0))
                .isLessThan(8);
    }

    @Test
    void C_macroMaySelectTwoNonOverlappingScopes() {
        CandidateSpot zone = linearZone(8, 90);
        List<CandidateSpot> scopes = deriver().derive(zone, contextWithBoat(3.2));
        assertThat(scopes.size()).isGreaterThanOrEqualTo(2);
        Set<UUID> used = new HashSet<>();
        int selected = 0;
        for (CandidateSpot scope : scopes) {
            boolean overlap = scope.getZoneMembers().stream()
                    .map(CandidateSpot::getFishingTargetId)
                    .anyMatch(used::contains);
            if (!overlap) {
                scope.getZoneMembers().forEach(m -> used.add(m.getFishingTargetId()));
                selected++;
            }
        }
        assertThat(selected).isGreaterThanOrEqualTo(2);
    }

    @Test
    void D_scopesNeverDoubleCountMembers() {
        CandidateSpot zone = linearZone(8, 90);
        List<CandidateSpot> scopes = deriver().derive(zone, contextWithBoat(3.2));
        Set<UUID> seen = new HashSet<>();
        for (CandidateSpot scope : scopes) {
            for (CandidateSpot member : scope.getZoneMembers()) {
                assertThat(seen.add(member.getFishingTargetId())).isTrue();
            }
        }
    }

    @Test
    void E_polygonKeepsPrimitivesWithoutSingleEdgePath() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 900);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        Polygon flat = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 220);
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setType(FeatureType.FLAT);
        spot.setSourceGeometry(flat);
        spot.setLocation(flat.getInteriorPoint());
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched).isNotEmpty();
        assertThat(enriched).noneMatch(item -> item.getTargetKind() == TargetKind.AREA);
        assertThat(enriched.stream().anyMatch(item -> item.getTargetKind() == TargetKind.POINT
                || item.getTargetKind() == TargetKind.PATH)).isTrue();
    }

    @Test
    void F_largeZoneDoesNotRequireFullAllPairs() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LocalWaterPathEstimator estimator = new LocalWaterPathEstimator(new LocalMetricCrs());
        PlanningProperties.Spatial spatial = new PlanningProperties.Spatial();
        spatial.setPairwiseFullNodeLimit(48);
        ZoneNavGraph graph = estimator.buildGraph(UUID.randomUUID(), water, lake, spatial);
        assertThat(graph.nodeCount()).isGreaterThan(0);
        int theoretical = graph.theoreticalFullPairCount();
        int neighborEdges = graph.edges().size();
        assertThat(neighborEdges).isLessThan(theoretical);
    }

    @Test
    void G_lazyWaterPathReusesGraphWithoutRasterRebuild() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LocalWaterPathEstimator estimator = new LocalWaterPathEstimator(new LocalMetricCrs());
        ZoneNavGraph graph = estimator.buildGraph(UUID.randomUUID(), water, lake, new PlanningProperties.Spatial());
        Point a = point(PlanningFixtures.HEAD_LNG - metersToLng(80), PlanningFixtures.HEAD_LAT);
        Point b = point(PlanningFixtures.HEAD_LNG + metersToLng(80), PlanningFixtures.HEAD_LAT);
        var first = graph.shortest(a, b, 6);
        var second = graph.shortest(a, b, 6);
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().meters()).isEqualTo(first.get().meters());
    }

    @Test
    void K_clockwiseAndCounterClockwiseAreDifferentVisitOptionKeys() {
        RankedCandidate candidate = RoutePlannerHarness.candidate(
                UUID.randomUUID(),
                PlanningFixtures.HEAD_LNG,
                PlanningFixtures.HEAD_LAT,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.ISLAND_EDGE
        );
        candidate.spot().setFishingTargetId(UUID.randomUUID());
        candidate.spot().setTargetKind(TargetKind.PATH);
        candidate.spot().setClosedLoop(true);
        Point p = point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        candidate.spot().setPortals(List.of(new VisitPortal("a", p), new VisitPortal("b", p)));
        var options = new VisitOptionFactory().options(List.of(candidate), new PlanningProperties.Spatial());
        assertThat(options).hasSize(2);
        assertThat(options.get(0).key()).isNotEqualTo(options.get(1).key());
        assertThat(options.get(0).traversal()).isNotEqualTo(options.get(1).traversal());
        assertThat(options.get(0).key().cacheKey()).isNotEqualTo(options.get(1).key().cacheKey());
    }

    private static ZoneVisitScopeDeriver deriver() {
        return new ZoneVisitScopeDeriver(new SnapshotWaterPathService(null));
    }

    private static CandidateSpot linearZone(int n, double spacingM) {
        List<CandidateSpot> members = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Point location = point(
                    PlanningFixtures.HEAD_LNG + metersToLng(i * spacingM),
                    PlanningFixtures.HEAD_LAT);
            CandidateSpot member = new CandidateSpot();
            member.setFeatureId(UUID.randomUUID());
            member.setFishingTargetId(UUID.randomUUID());
            member.setCoverageIds(List.of(member.getFishingTargetId()));
            member.setTargetKind(TargetKind.POINT);
            member.setType(FeatureType.DROP_OFF);
            member.setLocation(location);
            member.setEntryPoint(location);
            member.setExitPoint(location);
            member.setTargetGeometry(location);
            members.add(member);
        }
        CandidateSpot zone = new CandidateSpot();
        zone.setZoneId(UUID.randomUUID());
        zone.setFeatureId(zone.getZoneId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setZoneMembers(members);
        zone.setLocation(members.get(0).getLocation());
        zone.setEntryPoint(members.get(0).getEntryPoint());
        zone.setExitPoint(members.get(n - 1).getExitPoint());
        zone.setTargetGeometry(ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 900));
        return zone;
    }

    private static CandidateSpot copyZone(CandidateSpot zone) {
        CandidateSpot copy = new CandidateSpot();
        copy.setZoneId(zone.getZoneId());
        copy.setFeatureId(zone.getFeatureId());
        copy.setTargetKind(TargetKind.ZONE);
        copy.setZoneMembers(zone.getZoneMembers());
        copy.setLocation(zone.getLocation());
        copy.setEntryPoint(zone.getEntryPoint());
        copy.setExitPoint(zone.getExitPoint());
        copy.setTargetGeometry(zone.getTargetGeometry());
        return copy;
    }

    private static com.aifishing.planning.service.PlanningContext contextWithBoat(double cruiseKmh) {
        var context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        EffectiveBoatCapability boat = new EffectiveBoatCapability(
                cruiseKmh, 20.0, 20.0, 20.0, 20.0, true, 8.0, WindWaveCapability.MEDIUM, 0, Map.of());
        return new com.aifishing.planning.service.PlanningContext(
                context.trip(),
                context.lake(),
                context.boat(),
                context.access(),
                context.geometry(),
                context.restrictions(),
                context.regulationCoverageStatus(),
                context.weather(),
                context.gearTypes(),
                context.profile(),
                context.strategyRun(),
                context.properties(),
                context.warnings(),
                context.baselineBoatCapability(),
                boat,
                context.launch(),
                null
        );
    }

    private static Point point(double lng, double lat) {
        Point created = FACTORY.createPoint(new Coordinate(lng, lat));
        created.setSRID(4326);
        return created;
    }

    private static double metersToLng(double meters) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(PlanningFixtures.HEAD_LAT)));
    }
}
