package com.aifishing.planning.spatial;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository.ZonePathKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingZoneWaterPathFlushTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @AfterEach
    void clearProfiler() {
        GenerateProfiler.clear();
    }

    @Test
    void bufferedMissWritesNothingUntilFlushAndSecondHopHitsMemory() {
        LakeFishingWaterPathRepository repository = mock(LakeFishingWaterPathRepository.class);
        when(repository.findZonePathKeys(any())).thenReturn(List.of());
        SnapshotWaterPathService service = new SnapshotWaterPathService(repository);
        UUID snapshotId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        SpatialSnapshotView view = view(snapshotId);
        PendingZoneWaterPaths pending = new PendingZoneWaterPaths();
        Point from = point(PlanningFixtures.HEAD_LNG - metersToLng(200), PlanningFixtures.HEAD_LAT);
        Point to = point(PlanningFixtures.HEAD_LNG + metersToLng(200), PlanningFixtures.HEAD_LAT);
        PlanningProperties.Spatial spatial = new PlanningProperties.Spatial();

        GenerateProfiler profiler = GenerateProfiler.begin();
        var first = service.path(view, zoneId, from, to, spatial, pending);
        long astarAfterFirst = profiler.counter("astarCalls");
        var second = service.path(view, zoneId, from, to, spatial, pending);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().meters()).isEqualTo(first.get().meters());
        assertThat(second.get().path().equalsExact(first.get().path())).isTrue();
        assertThat(profiler.counter("astarCalls")).isEqualTo(astarAfterFirst);
        assertThat(astarAfterFirst).isEqualTo(1);
        assertThat(pending.size()).isEqualTo(1);
        assertThat(profiler.counter("waterPathCacheHits")).isEqualTo(1);
        assertThat(profiler.counter("waterPathCacheMisses")).isEqualTo(1);
        assertThat(profiler.zoneSubPlanner().toMap().get("stagesMs").toString()).doesNotContain("waterPathPersist");
        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(any());
        verify(repository, never()).findZonePathKeys(any());
        verify(repository, never()).findBySpatialPlanningSnapshotIdAndZoneIdAndFromKeyAndToKey(any(), any(), any(), any());

        service.flushPending(pending);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LakeFishingWaterPath>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        LakeFishingWaterPath row = saved.getValue().getFirst();
        assertThat(row.getSpatialPlanningSnapshotId()).isEqualTo(snapshotId);
        assertThat(row.getZoneId()).isEqualTo(zoneId);
        assertThat(row.isPrecomputed()).isFalse();
        assertThat(pending.size()).isZero();

        when(repository.findZonePathKeys(snapshotId)).thenReturn(List.of(key(row)));
        PendingZoneWaterPaths again = new PendingZoneWaterPaths();
        again.add(snapshotId, zoneId, row.getFromKey(), row.getToKey(), first.get(), "water-nav-v3");
        service.flushPending(again);
        verify(repository, times(1)).saveAll(any());
        assertThat(again.size()).isZero();
    }

    @Test
    void deferredGeometryMatchesSynchronousPersist() {
        LakeFishingWaterPathRepository repository = mock(LakeFishingWaterPathRepository.class);
        when(repository.findBySpatialPlanningSnapshotIdAndZoneIdAndFromKeyAndToKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        SnapshotWaterPathService service = new SnapshotWaterPathService(repository);
        UUID zoneId = UUID.randomUUID();
        Point from = point(PlanningFixtures.HEAD_LNG - metersToLng(180), PlanningFixtures.HEAD_LAT);
        Point to = point(PlanningFixtures.HEAD_LNG + metersToLng(180), PlanningFixtures.HEAD_LAT);
        PlanningProperties.Spatial spatial = new PlanningProperties.Spatial();
        PendingZoneWaterPaths pending = new PendingZoneWaterPaths();

        var deferred = service.path(view(UUID.randomUUID()), zoneId, from, to, spatial, pending);
        var synchronous = service.path(view(UUID.randomUUID()), zoneId, from, to, spatial, null);

        assertThat(deferred).isPresent();
        assertThat(synchronous).isPresent();
        assertThat(deferred.get().meters()).isEqualTo(synchronous.get().meters());
        assertThat(deferred.get().path().equalsExact(synchronous.get().path())).isTrue();
        verify(repository, never()).saveAll(any());
        verify(repository, times(1)).save(any());
    }

    @Test
    void buffersDoNotLeakAcrossGenerateContexts() {
        PlanningContext first = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20), new PlanningProperties(), RoutePlannerHarness.launch());
        PlanningContext second = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 8, 20), new PlanningProperties(), RoutePlannerHarness.launch());
        SpatialSnapshotView snapshot = view(UUID.randomUUID());
        PlanningContext withSnapshot = first.withSnapshot(snapshot);

        assertThat(withSnapshot.pendingZoneWaterPaths()).isSameAs(first.pendingZoneWaterPaths());
        assertThat(second.pendingZoneWaterPaths()).isNotSameAs(first.pendingZoneWaterPaths());

        UUID zoneId = UUID.randomUUID();
        first.pendingZoneWaterPaths().add(
                snapshot.id(),
                zoneId,
                "a",
                "b",
                new LocalWaterPathEstimator.PathEstimate(null, 12, 1),
                "water-nav-v3");
        assertThat(first.pendingZoneWaterPaths().size()).isEqualTo(1);
        assertThat(withSnapshot.pendingZoneWaterPaths().size()).isEqualTo(1);
        assertThat(second.pendingZoneWaterPaths().size()).isZero();
    }

    @Test
    void failedFlushRestoresTheBuffer() {
        LakeFishingWaterPathRepository repository = mock(LakeFishingWaterPathRepository.class);
        when(repository.findZonePathKeys(any())).thenThrow(new IllegalStateException("rds down"));
        SnapshotWaterPathService service = new SnapshotWaterPathService(repository);
        PendingZoneWaterPaths pending = new PendingZoneWaterPaths();
        UUID snapshotId = UUID.randomUUID();
        pending.add(
                snapshotId,
                UUID.randomUUID(),
                "a",
                "b",
                new LocalWaterPathEstimator.PathEstimate(null, 4, 1),
                "water-nav-v3");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.flushPending(pending))
                .isInstanceOf(IllegalStateException.class);
        assertThat(pending.size()).isEqualTo(1);
        verify(repository, never()).saveAll(any());
    }

    private static SpatialSnapshotView view(UUID snapshotId) {
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        LakePlanningGeometry lake = new LakePlanningGeometry(water, List.of());
        LakeNavRaster raster = new LakeNavRasterBuilder(new LocalMetricCrs()).build(lake, new PlanningProperties.Spatial());
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setNavigationVersion("water-nav-v3");
        return new SpatialSnapshotView(snapshot, List.of(), java.util.Map.of(), List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), raster);
    }

    private static ZonePathKey key(LakeFishingWaterPath row) {
        return new ZonePathKey() {
            @Override
            public UUID getZoneId() {
                return row.getZoneId();
            }

            @Override
            public String getFromKey() {
                return row.getFromKey();
            }

            @Override
            public String getToKey() {
                return row.getToKey();
            }
        };
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
