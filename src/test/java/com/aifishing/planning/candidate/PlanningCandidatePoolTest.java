package com.aifishing.planning.candidate;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.PlanningInputTargetSource;
import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningCandidatePoolTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private FakeLoader loader;
    private PlanningCandidatePool pool;
    private UUID runId;

    @BeforeEach
    void setUp() {
        loader = new FakeLoader();
        pool = new DefaultPlanningCandidatePool(loader, new PlanningOpportunityDeduper());
        runId = UUID.randomUUID();
    }

    @Test
    void aiIgnoresTemplateTargets() {
        stubFrozen(List.of(templatePoint("tmpl", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT)));
        CandidateSpot ai = aiSpot("ai", PlanningFixtures.HEAD_LNG + 0.01, PlanningFixtures.HEAD_LAT);
        PlanningCandidatePool.Result result = pool.build(
                PlanningMode.AI, List.of(ai), contextWithoutSnapshot(), runId);
        assertThat(result.candidates()).extracting(CandidateSpot::getCandidateSource)
                .containsOnly(CandidateSource.AI);
        assertThat(result.candidates()).extracting(CandidateSpot::getFeatureId)
                .containsExactly(ai.getFeatureId());
    }

    @Test
    void hybridUnionsTemplateAndAi() {
        stubFrozen(List.of(templatePoint("tmpl", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT)));
        CandidateSpot ai = aiSpot("ai", PlanningFixtures.HEAD_LNG + 0.02, PlanningFixtures.HEAD_LAT);
        PlanningCandidatePool.Result result = pool.build(
                PlanningMode.HYBRID, List.of(ai), contextWithoutSnapshot(), runId);
        assertThat(result.candidates()).extracting(CandidateSpot::getCandidateSource)
                .containsExactlyInAnyOrder(CandidateSource.TEMPLATE, CandidateSource.AI);
    }

    @Test
    void customExcludesAiOutsideTemplate() {
        stubFrozen(List.of(templatePoint("tmpl", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT)));
        CandidateSpot ai = aiSpot("ai", PlanningFixtures.HEAD_LNG + 0.03, PlanningFixtures.HEAD_LAT);
        PlanningCandidatePool.Result result = pool.build(
                PlanningMode.CUSTOM, List.of(ai), contextWithoutSnapshot(), runId);
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).getCandidateSource()).isEqualTo(CandidateSource.TEMPLATE);
    }

    @Test
    void zoneContributesIntersectingTargetsOnlyWithoutFullCoverage() {
        UUID insideId = UUID.nameUUIDFromBytes("inside".getBytes());
        UUID outsideId = UUID.nameUUIDFromBytes("outside".getBytes());
        LakeFishingTarget inside = snapshotTarget(insideId, PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        LakeFishingTarget outside = snapshotTarget(
                outsideId,
                PlanningFixtures.HEAD_LNG + 0.05,
                PlanningFixtures.HEAD_LAT + 0.05);
        Polygon zone = square(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 0.01);
        stubFrozen(List.of(templateZone("zone", zone)));
        PlanningContext context = contextWithSnapshot(List.of(inside, outside));
        PlanningCandidatePool.Result result = pool.build(
                PlanningMode.CUSTOM, List.of(), context, runId);
        assertThat(result.candidates()).extracting(CandidateSpot::getFishingTargetId)
                .contains(insideId)
                .doesNotContain(outsideId);
        assertThat(result.candidates()).allMatch(spot -> spot.getCandidateSource() == CandidateSource.TEMPLATE);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void emptyZoneIntersectionWarnsWithoutFailing() {
        UUID outsideId = UUID.nameUUIDFromBytes("far".getBytes());
        LakeFishingTarget outside = snapshotTarget(
                outsideId,
                PlanningFixtures.HEAD_LNG + 0.08,
                PlanningFixtures.HEAD_LAT + 0.08);
        Polygon zone = square(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 0.005);
        stubFrozen(List.of(templateZone("empty-zone", zone)));
        PlanningCandidatePool.Result result = pool.build(
                PlanningMode.CUSTOM, List.of(), contextWithSnapshot(List.of(outside)), runId);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).contains(DefaultPlanningCandidatePool.TEMPLATE_ZONE_EMPTY);
    }

    @Test
    void dedupPrecedenceRequiredOverTemplateOverAiKeepsEvidence() {
        CandidateSpot required = aiSpot("req", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        required.setCandidateSource(CandidateSource.REQUIRED);
        required.setSourceFeatureIds(List.of(UUID.nameUUIDFromBytes("req-feat".getBytes())));
        CandidateSpot template = aiSpot("tmpl", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        template.setCandidateSource(CandidateSource.TEMPLATE);
        template.setEvidenceTypes(List.of(FeatureType.HUMP));
        CandidateSpot ai = aiSpot("ai", PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        ai.setCandidateSource(CandidateSource.AI);
        ai.setSourceFeatureIds(List.of(UUID.nameUUIDFromBytes("ai-feat".getBytes())));
        ai.setEvidenceTypes(List.of(FeatureType.POINT));

        List<CandidateSpot> deduped = new PlanningOpportunityDeduper().dedupe(List.of(ai, template, required));
        assertThat(deduped).hasSize(1);
        CandidateSpot kept = deduped.get(0);
        assertThat(kept.getCandidateSource()).isEqualTo(CandidateSource.REQUIRED);
        assertThat(kept.getUnderlyingSources()).contains(CandidateSource.TEMPLATE, CandidateSource.AI);
        assertThat(kept.getEvidenceTypes()).contains(FeatureType.HUMP, FeatureType.POINT);
        assertThat(kept.getSourceFeatureIds()).contains(
                UUID.nameUUIDFromBytes("req-feat".getBytes()),
                UUID.nameUUIDFromBytes("ai-feat".getBytes()));
    }

    private void stubFrozen(List<TripPlanningInputTarget> targets) {
        TripPlanningInputSnapshot snapshot = new TripPlanningInputSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setPlanningRunId(runId);
        snapshot.setMode(PlanningMode.HYBRID);
        loader.snapshot = snapshot;
        loader.targets = targets;
    }

    private PlanningContext contextWithoutSnapshot() {
        return RoutePlannerHarness.context(
                calmWeather(), new com.aifishing.planning.PlanningProperties(), RoutePlannerHarness.launch());
    }

    private PlanningContext contextWithSnapshot(List<LakeFishingTarget> targets) {
        PlanningContext base = contextWithoutSnapshot();
        SpatialPlanningSnapshot snap = new SpatialPlanningSnapshot();
        snap.setId(UUID.randomUUID());
        SpatialSnapshotView view = new SpatialSnapshotView(
                snap, targets, Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        return base.withSnapshot(view);
    }

    private static CandidateSpot aiSpot(String key, double lng, double lat) {
        CandidateSpot spot = new CandidateSpot();
        UUID id = UUID.nameUUIDFromBytes(key.getBytes());
        spot.setFeatureId(id);
        spot.setFishingTargetId(id);
        spot.setType(FeatureType.HUMP);
        spot.setLocation(RoutePlannerHarness.point(lng, lat));
        spot.setTargetGeometry(spot.getLocation());
        spot.setTargetKind(TargetKind.POINT);
        spot.setStrategyWeight(0.6);
        spot.setFeatureConfidence(0.8);
        spot.setCandidateSource(CandidateSource.AI);
        spot.setPipeline(Pipeline.GIS);
        spot.setAnalysisVersion("plan-v1");
        return spot;
    }

    private static TripPlanningInputTarget templatePoint(String name, double lng, double lat) {
        TripPlanningInputTarget target = new TripPlanningInputTarget();
        target.setId(UUID.nameUUIDFromBytes(name.getBytes()));
        target.setSource(PlanningInputTargetSource.TEMPLATE);
        target.setKind(TemplateTargetKind.POINT);
        target.setName(name);
        target.setGeometry(RoutePlannerHarness.point(lng, lat));
        target.setSortOrder(0);
        return target;
    }

    private static TripPlanningInputTarget templateZone(String name, Polygon zone) {
        TripPlanningInputTarget target = new TripPlanningInputTarget();
        target.setId(UUID.nameUUIDFromBytes(name.getBytes()));
        target.setSource(PlanningInputTargetSource.TEMPLATE);
        target.setKind(TemplateTargetKind.ZONE);
        target.setName(name);
        target.setGeometry(zone);
        target.setSortOrder(0);
        return target;
    }

    private static LakeFishingTarget snapshotTarget(UUID id, double lng, double lat) {
        LakeFishingTarget target = new LakeFishingTarget();
        target.setId(id);
        target.setTargetKind(TargetKind.POINT);
        target.setSemanticType(FeatureType.HUMP);
        target.setGeometry(RoutePlannerHarness.point(lng, lat));
        target.setRepresentativePoint(RoutePlannerHarness.point(lng, lat));
        target.setSourceFeatureIds(List.of(id));
        target.setFeaturePipeline(Pipeline.GIS);
        target.setFeatureAnalysisVersion("plan-v1");
        return target;
    }

    private static Polygon square(double lng, double lat, double halfDeg) {
        Polygon polygon = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(lng - halfDeg, lat - halfDeg),
                new Coordinate(lng + halfDeg, lat - halfDeg),
                new Coordinate(lng + halfDeg, lat + halfDeg),
                new Coordinate(lng - halfDeg, lat + halfDeg),
                new Coordinate(lng - halfDeg, lat - halfDeg)
        });
        polygon.setSRID(4326);
        return polygon;
    }

    private static WeatherContext calmWeather() {
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

    private static final class FakeLoader extends PlanningInputTargetLoader {
        private TripPlanningInputSnapshot snapshot;
        private List<TripPlanningInputTarget> targets = List.of();

        FakeLoader() {
            super(null, null);
        }

        @Override
        public Optional<TripPlanningInputSnapshot> findSnapshot(UUID planningRunId) {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public List<TripPlanningInputTarget> findTargets(UUID snapshotId) {
            return targets == null ? List.of() : targets;
        }
    }
}
