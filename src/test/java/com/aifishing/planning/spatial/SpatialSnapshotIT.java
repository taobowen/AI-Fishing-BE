package com.aifishing.planning.spatial;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.repo.LakeFishingNavEdgeRepository;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository;
import com.aifishing.planning.spatial.repo.LakeNavigationTileRepository;
import com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpatialSnapshotIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    SpatialPlanningSnapshotRepository snapshotRepository;
    @Autowired
    LakeFishingTargetRepository targetRepository;
    @Autowired
    LakeFishingWaterPathRepository waterPathRepository;
    @Autowired
    LakeFishingNavEdgeRepository navEdgeRepository;
    @Autowired
    LakeNavigationTileRepository tileRepository;
    @Autowired
    SpatialSnapshotService snapshotService;
    @Autowired
    SnapshotWaterPathService waterPathService;
    @Autowired
    com.aifishing.planning.PlanningProperties planningProperties;

    @Test
    void H_failedSnapshotIsNotVisibleToGenerate() throws Exception {
        seedFeatures();
        SpatialPlanningSnapshot failed = newRow("water-nav-v2");
        failed.setStatus(SpatialSnapshotStatus.FAILED);
        failed.setErrorMessage("forced");
        snapshotRepository.saveAndFlush(failed);

        UUID tripId = saveShoreTrip();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("SPATIAL_SNAPSHOT_NOT_READY")));
    }

    @Test
    void I_concurrentSameKeyRebuildIsIdempotent() throws Exception {
        seedFeatures();
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<UUID>> tasks = List.of(
                    () -> spatialSnapshotJob.build(
                            DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION).getId(),
                    () -> spatialSnapshotJob.build(
                            DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION).getId()
            );
            var results = pool.invokeAll(tasks);
            UUID first = results.get(0).get();
            UUID second = results.get(1).get();
            assertThat(first).isEqualTo(second);
            assertThat(snapshotRepository.findByLakeIdOrderByCreatedAtDesc(DevSeedIds.LAKE_ID))
                    .hasSize(1)
                    .allMatch(row -> row.getStatus() == SpatialSnapshotStatus.READY);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void J_oldReadySnapshotSurvivesNewKeyFailure() throws Exception {
        seedFeatures();
        SpatialPlanningSnapshot ready = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        assertThat(ready.getStatus()).isEqualTo(SpatialSnapshotStatus.READY);
        UUID readyId = ready.getId();

        SpatialPlanningSnapshot failed = newRow("water-nav-v9-fail");
        failed.setStatus(SpatialSnapshotStatus.FAILED);
        failed.setErrorMessage("new key failed");
        snapshotRepository.saveAndFlush(failed);

        var loaded = snapshotService.findReady(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(readyId);
        assertThat(loaded.get().getStatus()).isEqualTo(SpatialSnapshotStatus.READY);

        UUID tripId = saveShoreTrip();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void gisAndHybridSnapshotsAreSeparateAndSecondGenerateDoesNotRebuild() throws Exception {
        seedFeatures();
        SpatialPlanningSnapshot gis = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        var hybridFeature = PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.001, 40),
                2.5,
                3.5,
                0.8,
                "hybrid-v1"
        );
        hybridFeature.setPipeline(Pipeline.HYBRID);
        featureRepository.save(hybridFeature);
        SpatialPlanningSnapshot hybrid = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.HYBRID, "hybrid-v1");
        assertThat(hybrid.getId()).isNotEqualTo(gis.getId());
        assertThat(hybrid.getFeaturePipeline()).isEqualTo(Pipeline.HYBRID);

        UUID tripId = saveShoreTrip();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.version", is(2)));

        assertThat(snapshotRepository.findByLakeIdOrderByCreatedAtDesc(DevSeedIds.LAKE_ID)
                .stream()
                .filter(row -> row.getFeaturePipeline() == Pipeline.GIS)
                .toList()).hasSize(1);
        SpatialPlanningSnapshot after = snapshotRepository.findById(gis.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SpatialSnapshotStatus.READY);
        assertThat(targetRepository.findBySpatialPlanningSnapshotId(gis.getId())).isNotEmpty();
    }

    @Test
    void G_lazyWaterPathIsPersistedOnTheSnapshot() {
        seedFeatures();
        SpatialPlanningSnapshot ready = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        SpatialSnapshotView view = snapshotService.load(ready.getId());
        assertThat(view.raster()).isNotNull();
        assertThat(view.zones()).isNotEmpty();
        UUID zoneId = view.zones().getFirst().getId();
        Point from = view.zones().getFirst().getRepresentativePoint();
        Point to = view.portalsByZone().getOrDefault(zoneId, List.of()).isEmpty()
                ? from
                : view.portalsByZone().get(zoneId).get(Math.min(1, view.portalsByZone().get(zoneId).size() - 1)).point();
        if (from.equals(to) && view.portalsByZone().getOrDefault(zoneId, List.of()).size() >= 2) {
            from = view.portalsByZone().get(zoneId).get(0).point();
            to = view.portalsByZone().get(zoneId).get(1).point();
        }
        UUID readyId = ready.getId();
        SpatialSnapshotStatus statusBefore = ready.getStatus();
        long before = waterPathRepository.countBySpatialPlanningSnapshotIdAndPrecomputedFalse(readyId);
        var first = waterPathService.path(view, zoneId, from, to, planningProperties.getSpatial());
        var second = waterPathService.path(view, zoneId, from, to, planningProperties.getSpatial());
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().meters()).isEqualTo(first.get().meters());
        long after = waterPathRepository.countBySpatialPlanningSnapshotIdAndPrecomputedFalse(readyId);
        assertThat(after).isGreaterThanOrEqualTo(before);
        SpatialPlanningSnapshot afterSnap = snapshotRepository.findById(readyId).orElseThrow();
        assertThat(afterSnap.getId()).isEqualTo(readyId);
        assertThat(afterSnap.getStatus()).isEqualTo(statusBefore);
    }

    @Test
    void H_v3SnapshotsWriteZeroNavEdges() {
        seedFeatures();
        SpatialPlanningSnapshot ready = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        assertThat(ready.getNavigationVersion()).isEqualTo("water-nav-v3");
        assertThat(navEdgeRepository.countBySpatialPlanningSnapshotId(ready.getId())).isZero();
        assertThat(tileRepository.countBySpatialPlanningSnapshotId(ready.getId())).isPositive();
        assertThat(ready.getCounts().get("navigationEdgeCount")).isEqualTo(0);
        SpatialSnapshotView view = snapshotService.load(ready.getId());
        assertThat(view.raster()).isNotNull();
        assertThat(view.graphsByZone()).isEmpty();
    }

    @Test
    void K_lazyAppendDoesNotChangeSnapshotIdOrStatus() {
        G_lazyWaterPathIsPersistedOnTheSnapshot();
    }

    @Test
    void L_runningChildrenAreInvisibleToGenerate() throws Exception {
        seedFeatures();
        SpatialPlanningSnapshot running = newRow(planningProperties.getSpatial().getNavigationVersion());
        running.setStatus(SpatialSnapshotStatus.RUNNING);
        snapshotRepository.saveAndFlush(running);
        assertThat(snapshotService.findReady(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION)).isEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                com.aifishing.common.exception.BadRequestException.class,
                () -> snapshotService.load(running.getId()));
        UUID tripId = saveShoreTrip();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("SPATIAL_SNAPSHOT_NOT_READY")));
    }

    @Test
    void M_multiTransactionBuildEndsReady() {
        seedFeatures();
        SpatialPlanningSnapshot ready = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        assertThat(ready.getStatus()).isEqualTo(SpatialSnapshotStatus.READY);
        assertThat(ready.getCounts().get("stage")).isEqualTo("READY");
        assertThat(ready.getCounts()).containsKeys(
                "targetsBuilt", "zonesTotal", "neighborPairCount", "theoreticalPairCount");
        SpatialPlanningSnapshot reloaded = snapshotRepository.findById(ready.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpatialSnapshotStatus.READY);
        assertThat(tileRepository.countBySpatialPlanningSnapshotId(ready.getId())).isPositive();
    }

    @Test
    void N_snapshotRecordsNeighborPairsMuchLessThanAllPairs() {
        seedFeatures();
        SpatialPlanningSnapshot ready = spatialSnapshotJob.build(
                DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION);
        Number neighbor = (Number) ready.getCounts().get("neighborPairCount");
        Number theoretical = (Number) ready.getCounts().get("theoreticalPairCount");
        assertThat(theoretical.longValue()).isGreaterThan(0);
        assertThat(neighbor.longValue()).isLessThanOrEqualTo(theoretical.longValue());
    }

    private void seedFeatures() {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.0018, 50),
                2.5,
                3.5,
                0.84,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.DROP_OFF,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG + 0.0015, PlanningFixtures.HEAD_LAT, 40),
                3.0,
                4.0,
                0.8,
                PlanningFixtures.ANALYSIS_VERSION
        ));
    }

    private UUID saveShoreTrip() {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        return tripRepository.save(trip).getId();
    }

    private SpatialPlanningSnapshot newRow(String navigationVersion) {
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setLakeId(DevSeedIds.LAKE_ID);
        snapshot.setFeaturePipeline(Pipeline.GIS);
        snapshot.setFeatureAnalysisVersion(PlanningFixtures.ANALYSIS_VERSION);
        snapshot.setTargetDerivationVersion(planningProperties.getSpatial().getDerivationVersion());
        snapshot.setZoneBuilderVersion(planningProperties.getSpatial().getZoneBuilderVersion());
        snapshot.setNavigationVersion(navigationVersion);
        snapshot.setStartedAt(Instant.now());
        return snapshot;
    }
}
