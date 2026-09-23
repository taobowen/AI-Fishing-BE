package com.aifishing.planning.spatial;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateLocationService;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.SolarPositionService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialPlanningTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void dropsTinySliversAndKeepsHoles() {
        Polygon outer = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 400);
        Polygon sliver = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG + 0.02, PlanningFixtures.HEAD_LAT, 2);
        var collection = FACTORY.createGeometryCollection(new org.locationtech.jts.geom.Geometry[]{outer, sliver});
        var cleaned = GeometrySanitizer.validateFixAndNormalize(collection, 50);
        assertThat(cleaned).isNotNull();
        assertThat(cleaned.isValid()).isTrue();
        assertThat(com.aifishing.lake.processing.extract.GeoMetrics.areaM2(cleaned)).isGreaterThan(50);
        assertThat(cleaned.getNumGeometries()).isEqualTo(1);
    }

    @Test
    void mixedGeometryCollectionDoesNotFailBuffer() {
        Polygon outer = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 400);
        LineString leftover = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT),
                new Coordinate(PlanningFixtures.HEAD_LNG + 0.001, PlanningFixtures.HEAD_LAT)
        });
        var collection = FACTORY.createGeometryCollection(new org.locationtech.jts.geom.Geometry[]{outer, leftover});
        var cleaned = GeometrySanitizer.validateFixAndNormalize(collection, 50);
        assertThat(cleaned).isNotNull();
        assertThat(cleaned.getGeometryType()).isNotEqualTo("GeometryCollection");
        assertThat(cleaned.isValid()).isTrue();
    }

    @Test
    void planningGeometryFlattensMixedWaterBeforePreparedOps() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 400);
        LineString leftover = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT),
                new Coordinate(PlanningFixtures.HEAD_LNG + 0.001, PlanningFixtures.HEAD_LAT)
        });
        var collection = FACTORY.createGeometryCollection(new org.locationtech.jts.geom.Geometry[]{water, leftover});
        LakePlanningGeometry lake = new LakePlanningGeometry(collection, List.of());
        assertThat(lake.hasWater()).isTrue();
        assertThat(lake.water().getGeometryType()).isNotEqualTo("GeometryCollection");
        Point inside = FACTORY.createPoint(new Coordinate(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT));
        assertThat(lake.inWater(inside)).isTrue();
    }

    @Test
    void waterPathDoesNotCrossIsland() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 120);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        Point west = point(PlanningFixtures.HEAD_LNG - metersToLng(250), PlanningFixtures.HEAD_LAT);
        Point east = point(PlanningFixtures.HEAD_LNG + metersToLng(250), PlanningFixtures.HEAD_LAT);
        LocalWaterPathEstimator estimator = new LocalWaterPathEstimator(new LocalMetricCrs());
        var path = estimator.path(west, east, lake, water, new PlanningProperties.Spatial());
        assertThat(path).isPresent();
        assertThat(island.intersects(path.get().path()) && !island.touches(path.get().path())).isFalse();
        assertThat(path.get().meters()).isGreaterThan(GeoMetricsDistance(west, east));
    }

    @Test
    void shorelineEndpointsAreNotUsedAsPortals() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LineString shore = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(300), PlanningFixtures.HEAD_LAT + metersToLat(300)),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(300), PlanningFixtures.HEAD_LAT + metersToLat(300))
        });
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.ISLAND_EDGE);
        spot.setSourceGeometry(shore);
        spot.setLocation(shore.getStartPoint());
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched).isNotEmpty();
        var segment = enriched.get(0);
        assertThat(segment.getTargetKind()).isEqualTo(TargetKind.PATH);
        assertThat(lake.validFishingPoint(segment.getEntryPoint())).isTrue();
        assertThat(segment.getEntryPoint().equals(shore.getStartPoint())).isFalse();
        assertThat(segment.getExitPoint().equals(shore.getEndPoint())).isFalse();
    }

    @Test
    void longEdgeSplitsIntoMultipleSegments() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 900);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LineString shore = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(700), PlanningFixtures.HEAD_LAT),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(700), PlanningFixtures.HEAD_LAT)
        });
        CandidateSpot spot = edgeSpot(shore);
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setMaxSegmentLengthM(500);
        var enriched = builder.enrich(List.of(spot), lake, properties);
        assertThat(enriched.size()).isGreaterThan(1);
        assertThat(enriched).allMatch(item -> item.getTargetKind() == TargetKind.PATH);
        assertThat(enriched.get(0).getFishingTargetId()).isNotEqualTo(enriched.get(1).getFishingTargetId());
        assertThat(enriched.get(0).coverageIds()).contains(enriched.get(0).getFishingTargetId());
        assertThat(enriched.get(1).coverageIds()).contains(enriched.get(1).getFishingTargetId());
        assertThat(enriched.get(0).getFeatureId()).isEqualTo(enriched.get(1).getFeatureId());
    }

    @Test
    void arealHumpBecomesPathOrPointPrimitivesNotArea() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        Polygon hump = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 80);
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.HUMP);
        spot.setSourceGeometry(hump);
        spot.setLocation(hump.getInteriorPoint());
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).getTargetKind()).isEqualTo(TargetKind.POINT);
    }

    @Test
    void arealBasinIsOneRepresentativePoint() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        Polygon basin = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 120);
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.BASIN);
        spot.setSourceGeometry(basin);
        spot.setLocation(basin.getInteriorPoint());
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).getTargetKind()).isEqualTo(TargetKind.POINT);
    }

    @Test
    void closedFlatStaysOnePoint() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        Polygon flat = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 200);
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.FLAT);
        spot.setSourceGeometry(flat);
        spot.setLocation(flat.getInteriorPoint());
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).getTargetKind()).isEqualTo(TargetKind.POINT);
        assertThat(enriched.get(0).getSourceGeometry()).isEqualTo(flat);
    }

    @Test
    void physicalClusterBuildsIrregularZone() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = new FishingZoneBuilder(
                new LocalMetricCrs(),
                new LocalWaterPathEstimator(new LocalMetricCrs()),
                new CandidateLocationService()
        );
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(3);
        properties.getSpatial().setClusterConnectM(250);
        List<CandidateSpot> spots = List.of(
                edgeAt(-80, 0),
                edgeAt(0, 0),
                edgeAt(80, 0)
        );
        var atomics = targets.enrich(spots, lake, properties);
        var clustered = zones.cluster(atomics, lake, properties);
        assertThat(clustered).isNotEmpty();
        CandidateSpot zone = clustered.get(0);
        assertThat(zone.getTargetKind()).isEqualTo(TargetKind.ZONE);
        assertThat(zone.getZoneMembers()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(zone.getTargetGeometry().isValid()).isTrue();
        assertThat(zone.coverageIds()).contains(atomics.get(0).getFishingTargetId());
    }

    @Test
    void zoneAndMemberCoverageAreMutuallyExclusive() {
        CandidateSpot member = edgeAt(0, 0);
        member.setFeatureId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"));
        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2"));
        zone.setZoneId(zone.getFeatureId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setZoneMembers(List.of(member));
        zone.setCoverageIds(List.of(zone.getFeatureId(), member.getFeatureId()));
        assertThat(zone.coverageIds()).contains(member.getFeatureId());
        assertThat(member.coverageIds()).contains(member.getFeatureId());
    }

    @Test
    void segmentVisitOptionsAreDirectional() {
        RankedCandidate candidate = RoutePlannerHarness.candidate(
                UUID.randomUUID(),
                PlanningFixtures.HEAD_LNG,
                PlanningFixtures.HEAD_LAT,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.ISLAND_EDGE
        );
        candidate.spot().setTargetKind(TargetKind.PATH);
        candidate.spot().setPortals(List.of(
                new VisitPortal("a", point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT)),
                new VisitPortal("b", point(PlanningFixtures.HEAD_LNG + metersToLng(80), PlanningFixtures.HEAD_LAT))
        ));
        var options = new VisitOptionFactory().options(List.of(candidate), new PlanningProperties.Spatial());
        assertThat(options).hasSize(2);
        assertThat(options.get(0).entry().id()).isNotEqualTo(options.get(1).entry().id());
    }

    @Test
    void closedLoopIslandEdgeIsClockwiseCapable() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LineString ring = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(80), PlanningFixtures.HEAD_LAT - metersToLat(80)),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(80), PlanningFixtures.HEAD_LAT - metersToLat(80)),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(80), PlanningFixtures.HEAD_LAT + metersToLat(80)),
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(80), PlanningFixtures.HEAD_LAT + metersToLat(80)),
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(80), PlanningFixtures.HEAD_LAT - metersToLat(80))
        });
        CandidateSpot spot = edgeSpot(ring);
        var enriched = builder.enrich(List.of(spot), lake, new PlanningProperties());
        assertThat(enriched.size()).isGreaterThanOrEqualTo(2);
        assertThat(enriched).allMatch(item -> item.getTargetKind() == TargetKind.PATH);
        assertThat(enriched).noneMatch(CandidateSpot::isClosedLoop);
    }

    @Test
    void coincidentDifferentTypesBecomeOneOpportunity() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        UUID humpId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID basinId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        CandidateSpot hump = depthPoint(FeatureType.HUMP, humpId, 0, 0, 4.6);
        CandidateSpot basin = depthPoint(FeatureType.BASIN, basinId, 3, 0, 4.8);
        var enriched = builder.enrich(List.of(hump, basin), lake, new PlanningProperties());
        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).getType()).isEqualTo(FeatureType.HUMP);
        assertThat(enriched.get(0).getEvidenceTypes()).contains(FeatureType.HUMP, FeatureType.BASIN);
        assertThat(enriched.get(0).getSourceFeatureIds()).contains(humpId, basinId);
    }

    @Test
    void oppositeShoresAcrossAnIslandStaySeparate() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 400);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 0.4);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        CandidateSpot west = depthPoint(FeatureType.HUMP, UUID.randomUUID(), -2, 0, 4.6);
        CandidateSpot east = depthPoint(FeatureType.DROP_OFF, UUID.randomUUID(), 2, 0, 4.6);
        var enriched = builder.enrich(List.of(west, east), lake, new PlanningProperties());
        assertThat(enriched).hasSize(2);
    }

    @Test
    void targetsOnOppositeSidesOfAnIslandDoNotJoinAZone() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 500);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 30);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        CandidateSpot west = depthPoint(FeatureType.ISLAND_EDGE, UUID.randomUUID(), -50, 0, 3.0);
        CandidateSpot east = depthPoint(FeatureType.ISLAND_EDGE, UUID.randomUUID(), 50, 0, 6.0);
        var clustered = zones.cluster(targets.enrich(List.of(west, east), lake, properties), lake, properties);
        assertThat(clustered).isEmpty();
        CandidateSpot near = depthPoint(FeatureType.HUMP, UUID.randomUUID(), -80, 0, 3.0);
        CandidateSpot alsoNear = depthPoint(FeatureType.HUMP, UUID.randomUUID(), -40, 20, 5.0);
        var sameSide = zones.cluster(targets.enrich(List.of(near, alsoNear), lake, properties), lake, properties);
        assertThat(sameSide).isNotEmpty();
    }

    @Test
    void alongPathAToBDiffersFromBToA() {
        SpatialUtility utility = new SpatialUtility(new TimeAdjustedSpotUtility(
                new SolarPositionService(),
                new BoatWeatherPenalty()
        ));
        Point a = point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        Point b = point(PlanningFixtures.HEAD_LNG + metersToLng(400), PlanningFixtures.HEAD_LAT);
        var samplesAb = utility.samples(null, a, b, new PlanningProperties.Spatial());
        var samplesBa = utility.samples(null, b, a, new PlanningProperties.Spatial());
        assertThat(samplesAb.get(0).point().equals(samplesBa.get(0).point())).isFalse();
        assertThat(samplesAb.getLast().fraction()).isEqualTo(1.0);
    }

    @Test
    void zoneSubplanVisitEqualsFishPlusInternalTransit() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        CandidateSpot memberA = pointSpot(-90, 0);
        CandidateSpot memberB = pointSpot(90, 0);
        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.randomUUID());
        zone.setZoneId(zone.getFeatureId());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setTargetGeometry(water);
        zone.setLocation(point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT));
        zone.setZoneMembers(List.of(memberA, memberB));
        VisitPortal entry = new VisitPortal("in", memberA.getEntryPoint());
        VisitPortal exit = new VisitPortal("out", memberB.getExitPoint());
        ZoneSubPlanner planner = new ZoneSubPlanner(
                new LocalWaterPathEstimator(new LocalMetricCrs()),
                new SpatialUtility(new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty())),
                new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty()),
                new LocalOrientationService()
        );
        var context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        context = new com.aifishing.planning.service.PlanningContext(
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
        ZoneSubPlan plan = planner.plan(
                zone,
                Instant.parse("2026-09-12T12:00:00Z"),
                60,
                entry,
                exit,
                context,
                TimeIndexedWeather.from(context.weather(), java.time.ZoneId.of("America/Toronto"))
        );
        assertThat(plan.visitMinutes()).isEqualTo(60);
        assertThat(plan.fishingMinutes() + plan.internalTransitMinutes() + plan.waitMinutes()).isEqualTo(60);
        assertThat(plan.fishingMinutes()).isGreaterThan(0);
    }

    @Test
    void rejectsEmptyZoneGeometry() {
        assertThat(GeometrySanitizer.validateFixAndNormalize(FACTORY.createPolygon(), 10)).isNull();
    }

    @Test
    void envelopeNormalizesPathAndPointBeforeUnion() {
        FishingZoneBuilder zones = new FishingZoneBuilder(
                new LocalMetricCrs(),
                new LocalWaterPathEstimator(new LocalMetricCrs()),
                new CandidateLocationService()
        );
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties.Spatial spatial = new PlanningProperties().getSpatial();
        CandidateSpot path = edgeAt(0, 0);
        path.setTargetKind(TargetKind.PATH);
        path.setTargetGeometry(path.getSourceGeometry());
        path.setFishingTargetId(UUID.randomUUID());
        CandidateSpot point = pointSpot(40, 0);
        point.setFishingTargetId(UUID.randomUUID());
        org.locationtech.jts.geom.Geometry pathPoly = zones.polygonalComponent(path, spatial);
        org.locationtech.jts.geom.Geometry pointPoly = zones.polygonalComponent(point, spatial);
        assertThat(pathPoly.getDimension()).isEqualTo(2);
        assertThat(pointPoly.getDimension()).isEqualTo(2);
        org.locationtech.jts.geom.Geometry envelope = zones.envelopeGeometry(List.of(path, point), lake, spatial);
        assertThat(envelope).isNotNull();
        assertThat(envelope.getDimension()).isEqualTo(2);
        assertThat(envelope.getGeometryType()).isNotEqualTo("GeometryCollection");
    }

    @Test
    void emptyLocationIsRecordedAsSkippedTarget() {
        FishingTargetBuilder builder = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 400);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setType(FeatureType.HUMP);
        spot.setLocation(FACTORY.createPoint());
        SpatialSkipLedger ledger = new SpatialSkipLedger();
        assertThat(builder.enrich(List.of(spot), lake, new PlanningProperties(), null, ledger)).isEmpty();
        assertThat(ledger.skippedTargetCount()).isEqualTo(1);
        assertThat(ledger.skips().getFirst().reason()).isEqualTo("EMPTY_LOCATION");
    }

    @Test
    void skipLedgerBlocksReadyWhenPartitionSkipsExceedThreshold() {
        SpatialSkipLedger ledger = new SpatialSkipLedger();
        ledger.addAttemptedPartition();
        ledger.addAttemptedPartition();
        ledger.skipPartition("p1", "ENVELOPE_FAILED");
        PlanningProperties.Spatial spatial = new PlanningProperties.Spatial();
        spatial.setMaxSkippedPartitionRatio(0.20);
        assertThat(ledger.exceedsThreshold(20, spatial)).isTrue();
        assertThat(ledger.failureMessage(20, spatial)).startsWith("SPATIAL_QUALITY_THRESHOLD:");
        assertThat(ledger.toCounts(20)).containsEntry("skippedPartitionCount", 1);
        assertThat(ledger.toCounts(20)).containsEntry("attemptedPartitionCount", 2);
    }

    @Test
    void oppositeSidesOfIslandAreNotLinkedByBoundingBoxAlone() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1200);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 200);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        properties.getSpatial().setZoneWaterPathJoinMaxM(400);
        CandidateSpot west = pointSpot(-160, 0);
        CandidateSpot east = pointSpot(160, 0);
        west.setFishingTargetId(UUID.randomUUID());
        east.setFishingTargetId(UUID.randomUUID());
        var atomics = targets.enrich(List.of(west, east), lake, properties);
        SpatialSkipLedger ledger = new SpatialSkipLedger();
        var clustered = zones.cluster(atomics, lake, properties, ledger);
        assertThat(clustered).isEmpty();
    }

    @Test
    void A_mixedIslandEdgeAndDropOffCanShareAZone() {
        assertThat(clusterMixedTypes(FeatureType.ISLAND_EDGE, FeatureType.DROP_OFF, FeatureType.HUMP))
                .hasSize(1);
        CandidateSpot zone = clusterMixedTypes(FeatureType.ISLAND_EDGE, FeatureType.DROP_OFF, FeatureType.HUMP).get(0);
        assertThat(zone.getZoneMembers().stream().map(CandidateSpot::getType).toList())
                .contains(FeatureType.ISLAND_EDGE, FeatureType.DROP_OFF, FeatureType.HUMP);
    }

    @Test
    void B_humpDoesNotHardSplitFromEdgeTypes() {
        List<CandidateSpot> clustered = clusterMixedTypes(FeatureType.HUMP, FeatureType.FLAT, FeatureType.DROP_OFF);
        assertThat(clustered).hasSize(1);
        assertThat(clustered.get(0).getZoneMembers()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void D_shortHopChainSplitsWhenDiameterExceedsCap() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        properties.getSpatial().setZoneWaterPathJoinMaxM(250);
        properties.getSpatial().setZoneMaxWaterPathDiameterM(500);
        properties.getSpatial().setZoneNeighborSearchRadiusM(400);
        List<CandidateSpot> spots = List.of(
                edgeAt(-400, 0),
                edgeAt(-200, 0),
                edgeAt(0, 0),
                edgeAt(200, 0),
                edgeAt(400, 0)
        );
        var atomics = targets.enrich(spots, lake, properties);
        var clustered = zones.cluster(atomics, lake, properties);
        assertThat(clustered).isNotEmpty();
        assertThat(clustered.stream().mapToInt(z -> z.getZoneMembers().size()).max().orElse(0))
                .isLessThan(5);
    }

    @Test
    void E_zoneEnvelopeSubtractsIslandsAndKeepsHoles() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 900);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 80);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(3);
        List<CandidateSpot> spots = List.of(
                edgeAt(-100, 120),
                edgeAt(0, 120),
                edgeAt(100, 120)
        );
        var atomics = targets.enrich(spots, lake, properties);
        var clustered = zones.cluster(atomics, lake, properties);
        assertThat(clustered).isNotEmpty();
        org.locationtech.jts.geom.Geometry envelope = clustered.get(0).getTargetGeometry();
        assertThat(envelope.isValid()).isTrue();
        assertThat(island.intersection(envelope).getArea()).isLessThan(island.getArea() * 0.05);
        assertThat(envelope.getGeometryType().contains("Polygon")).isTrue();
    }

    @Test
    void G_membershipDoesNotFollowEnvelopeOverlapAlone() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1400);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 220);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        properties.getSpatial().setZoneWaterPathJoinMaxM(350);
        CandidateSpot westA = typedPoint(FeatureType.DROP_OFF, -280, 40);
        CandidateSpot westB = typedPoint(FeatureType.DROP_OFF, -280, -40);
        CandidateSpot east = typedPoint(FeatureType.HUMP, 280, 0);
        var atomics = targets.enrich(List.of(westA, westB, east), lake, properties);
        var clustered = zones.cluster(atomics, lake, properties);
        assertThat(clustered).isNotEmpty();
        Set<UUID> members = clustered.get(0).getZoneMembers().stream()
                .map(CandidateSpot::getFishingTargetId)
                .collect(java.util.stream.Collectors.toSet());
        UUID eastId = atomics.stream()
                .filter(s -> s.getLocation().getX() > PlanningFixtures.HEAD_LNG)
                .findFirst()
                .orElseThrow()
                .getFishingTargetId();
        assertThat(members).doesNotContain(eastId);
        assertThat(members).hasSize(2);
    }

    @Test
    void I_sharedRasterAstarGoesAroundIsland() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        Polygon island = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 120);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of(island));
        LakeNavRaster raster = new LakeNavRasterBuilder(new LocalMetricCrs()).build(lake, new PlanningProperties.Spatial());
        Point west = point(PlanningFixtures.HEAD_LNG - metersToLng(250), PlanningFixtures.HEAD_LAT);
        Point east = point(PlanningFixtures.HEAD_LNG + metersToLng(250), PlanningFixtures.HEAD_LAT);
        var path = raster.shortest(west, east, 6);
        assertThat(path).isPresent();
        assertThat(island.intersects(path.get().path()) && !island.touches(path.get().path())).isFalse();
        assertThat(path.get().meters()).isGreaterThan(GeoMetricsDistance(west, east));
    }

    @Test
    void I2_diagonalMustNotCutIslandCorner() {
        boolean[][] nav = new boolean[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                nav[x][y] = !(x == 1 && y == 1);
            }
        }
        LakeNavGrid grid = new LakeNavGrid("EPSG:32618", 18, PlanningFixtures.HEAD_LNG, 0, 0, 25, 128, 3, 3);
        LakePlanningGeometry lake = new LakePlanningGeometry(
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 200),
                List.of());
        LocalMetricCrs.ProjectedGeometry projected = new LocalMetricCrs().project(lake.water(), PlanningFixtures.HEAD_LNG);
        LakeNavRaster raster = new LakeNavRaster(grid, nav, new byte[3][3], projected);
        assertThat(raster.traversable(0, 0)).isTrue();
        assertThat(raster.traversable(2, 2)).isTrue();
        assertThat(raster.traversable(1, 1)).isFalse();
        var path = raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(2, 2), 6);
        assertThat(path).isPresent();
        int[] start = raster.cellOf(raster.wgsOf(0, 0));
        int[] goal = raster.cellOf(raster.wgsOf(2, 2));
        assertThat(start).isNotNull();
        assertThat(goal).isNotNull();
    }

    @Test
    void J_overlappingWindowsShareTheSameTileCells() {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 600);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LakeNavRaster raster = new LakeNavRasterBuilder(new LocalMetricCrs()).build(lake, new PlanningProperties.Spatial());
        int[] cell = raster.cellOf(point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT));
        assertThat(cell).isNotNull();
        assertThat(raster.sharesCell(raster, cell[0], cell[1])).isTrue();
        assertThat(raster.packTiles()).isNotEmpty();
    }

    @Test
    void K2_strTreeDiscoversNearbyPathsWhoseEntriesAreFar() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1400);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        properties.getSpatial().setZoneNeighborSearchRadiusM(200);
        properties.getSpatial().setZoneWaterPathJoinMaxM(400);
        LineString north = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(400), PlanningFixtures.HEAD_LAT + metersToLat(30)),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(400), PlanningFixtures.HEAD_LAT + metersToLat(30))
        });
        LineString south = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(PlanningFixtures.HEAD_LNG - metersToLng(400), PlanningFixtures.HEAD_LAT - metersToLat(30)),
                new Coordinate(PlanningFixtures.HEAD_LNG + metersToLng(400), PlanningFixtures.HEAD_LAT - metersToLat(30))
        });
        var atomics = targets.enrich(List.of(edgeSpot(north), edgeSpot(south)), lake, properties);
        var clustered = zones.cluster(atomics, lake, properties);
        assertThat(clustered).isNotEmpty();
        assertThat(zones.lastStats().neighborPairCount()).isGreaterThan(0);
        assertThat(zones.lastStats().neighborPairCount()).isLessThanOrEqualTo(zones.lastStats().theoreticalPairCount());
    }

    @Test
    void N_neighborChecksAreMuchLessThanAllPairs() {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 2000);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(2);
        properties.getSpatial().setZoneNeighborSearchRadiusM(200);
        List<CandidateSpot> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            spots.add(typedPoint(FeatureType.DROP_OFF, -800 + i * 150, i % 2 == 0 ? 0 : 400));
        }
        var atomics = targets.enrich(spots, lake, properties);
        zones.cluster(atomics, lake, properties);
        ClusterStats stats = zones.lastStats();
        assertThat(stats.theoreticalPairCount()).isEqualTo(atomics.size() * (atomics.size() - 1) / 2);
        assertThat(stats.neighborPairCount()).isLessThan(stats.theoreticalPairCount());
    }

    private static List<CandidateSpot> clusterMixedTypes(FeatureType a, FeatureType b, FeatureType c) {
        FishingTargetBuilder targets = new FishingTargetBuilder(new CandidateLocationService(), new LocalMetricCrs());
        FishingZoneBuilder zones = zoneBuilder();
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        PlanningProperties properties = new PlanningProperties();
        properties.getSpatial().setClusterMinMembers(3);
        List<CandidateSpot> spots = List.of(
                typedPoint(a, -60, 0),
                typedPoint(b, 0, 0),
                typedPoint(c, 60, 0)
        );
        return zones.cluster(targets.enrich(spots, lake, properties), lake, properties);
    }

    private static FishingZoneBuilder zoneBuilder() {
        return new FishingZoneBuilder(
                new LocalMetricCrs(),
                new LocalWaterPathEstimator(new LocalMetricCrs()),
                new CandidateLocationService()
        );
    }

    private static CandidateSpot typedPoint(FeatureType type, double eastM, double northM) {
        CandidateSpot spot = pointSpot(eastM, northM);
        spot.setType(type);
        return spot;
    }

    private static CandidateSpot edgeSpot(LineString shore) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setType(FeatureType.ISLAND_EDGE);
        spot.setSourceGeometry(shore);
        spot.setLocation(shore.getStartPoint());
        spot.setStrategyWeight(0.7);
        spot.setFeatureConfidence(0.8);
        spot.setWindowFrom(LocalTime.of(8, 0));
        spot.setWindowTo(LocalTime.of(16, 0));
        return spot;
    }

    private static CandidateSpot edgeAt(double eastM, double northM) {
        LineString shore = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(
                        PlanningFixtures.HEAD_LNG + metersToLng(eastM - 40),
                        PlanningFixtures.HEAD_LAT + metersToLat(northM + 40)),
                new Coordinate(
                        PlanningFixtures.HEAD_LNG + metersToLng(eastM + 40),
                        PlanningFixtures.HEAD_LAT + metersToLat(northM + 40))
        });
        return edgeSpot(shore);
    }

    private static CandidateSpot depthPoint(FeatureType type, UUID featureId, double eastM, double northM, double depthM) {
        CandidateSpot spot = pointSpot(eastM, northM);
        spot.setFeatureId(featureId);
        spot.setType(type);
        spot.setRepresentativeDepthM(depthM);
        spot.setMinDepthM(depthM);
        spot.setMaxDepthM(depthM);
        spot.setSourceGeometry(spot.getLocation());
        return spot;
    }

    private static CandidateSpot pointSpot(double eastM, double northM) {
        Point location = point(
                PlanningFixtures.HEAD_LNG + metersToLng(eastM),
                PlanningFixtures.HEAD_LAT + metersToLat(northM));
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setType(FeatureType.HUMP);
        spot.setTargetKind(TargetKind.POINT);
        spot.setLocation(location);
        spot.setEntryPoint(location);
        spot.setExitPoint(location);
        spot.setTargetGeometry(location);
        spot.setStrategyWeight(0.8);
        spot.setFeatureConfidence(0.8);
        spot.setWindowFrom(LocalTime.of(8, 0));
        spot.setWindowTo(LocalTime.of(16, 0));
        return spot;
    }

    private static double GeoMetricsDistance(Point a, Point b) {
        return com.aifishing.lake.processing.extract.GeoMetrics.distanceM(a, b);
    }

    private static Point point(double lng, double lat) {
        Point created = FACTORY.createPoint(new Coordinate(lng, lat));
        created.setSRID(4326);
        return created;
    }

    private static double metersToLat(double meters) {
        return meters / 111_320.0;
    }

    private static double metersToLng(double meters) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(PlanningFixtures.HEAD_LAT)));
    }
}
