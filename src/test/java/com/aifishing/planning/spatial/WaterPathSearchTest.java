package com.aifishing.planning.spatial;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaterPathSearchTest {

    @AfterEach
    void clearProfiler() {
        GenerateProfiler.clear();
    }

    @Test
    void differentComponentsDoNotRunAstar() {
        LakeNavRaster raster = splitRaster();
        GenerateProfiler profiler = GenerateProfiler.begin();
        assertThat(raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(3, 0), 6)).isEmpty();
        assertThat(profiler.counter("astarCalls")).isZero();
        assertThat(profiler.counter("astarExpandedCells")).isZero();
        assertThat(raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(1, 0), 6)).isPresent();
        assertThat(profiler.counter("astarCalls")).isEqualTo(1);
    }

    @Test
    void unreachablePairIsCachedWithoutPersisting() {
        LakeNavRaster raster = splitRaster();
        UUID snapshotId = UUID.randomUUID();
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setNavigationVersion("water-nav-v3");
        SpatialSnapshotView view = new SpatialSnapshotView(
                snapshot, List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), raster);
        SnapshotWaterPathService service = new SnapshotWaterPathService(null);
        PlanningProperties.Spatial spatial = new PlanningProperties().getSpatial();
        UUID zoneId = UUID.randomUUID();
        Point from = raster.wgsOf(0, 0);
        Point to = raster.wgsOf(3, 0);
        GenerateProfiler profiler = GenerateProfiler.begin();

        assertThat(service.path(view, zoneId, from, to, spatial)).isEmpty();
        assertThat(service.path(view, zoneId, from, to, spatial)).isEmpty();

        assertThat(profiler.counter("astarCalls")).isZero();
        assertThat(profiler.counter("astarExpandedCells")).isZero();
        assertThat(profiler.counter("waterPathCacheHits")).isEqualTo(1);
        assertThat(view.waterPaths()).hasSize(2);
        assertThat(view.waterPaths().values()).allMatch(SpatialSnapshotView.CachedPath::isUnreachable);
    }

    @Test
    void sameSpatialPairIsSearchedOnce() {
        LakeNavRaster raster = openGrid();
        GenerateProfiler profiler = GenerateProfiler.begin();
        var first = raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(5, 5), 6).orElseThrow();
        for (int i = 0; i < 6; i++) {
            raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(i, i), 6);
        }
        var again = raster.shortest(raster.wgsOf(0, 0), raster.wgsOf(5, 5), 6).orElseThrow();
        assertThat(profiler.counter("astarCalls")).isEqualTo(6);
        LakeNavRaster fresh = openGrid();
        var independent = fresh.shortest(fresh.wgsOf(0, 0), fresh.wgsOf(5, 5), 6).orElseThrow();
        assertThat(again.meters()).isEqualTo(first.meters());
        assertThat(first.meters()).isEqualTo(independent.meters());
    }

    @Test
    void anotherZoneDoesNotRepeatTheSameCellSearch() {
        LakeNavRaster raster = splitRaster();
        SpatialSnapshotView view = view(raster);
        SnapshotWaterPathService service = new SnapshotWaterPathService(null);
        PlanningProperties.Spatial spatial = new PlanningProperties().getSpatial();
        Point from = raster.wgsOf(0, 0);
        Point to = raster.wgsOf(1, 0);
        GenerateProfiler profiler = GenerateProfiler.begin();
        assertThat(service.path(view, UUID.randomUUID(), from, to, spatial)).isPresent();
        assertThat(service.path(view, UUID.randomUUID(), from, to, spatial)).isPresent();
        assertThat(profiler.counter("astarCalls")).isEqualTo(1);
    }

    private static SpatialSnapshotView view(LakeNavRaster raster) {
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setNavigationVersion("water-nav-v3");
        return new SpatialSnapshotView(
                snapshot, List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), raster);
    }

    private static LakeNavRaster openGrid() {
        boolean[][] nav = new boolean[6][6];
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                nav[x][y] = true;
            }
        }
        return raster(nav, 6, 6);
    }

    private static LakeNavRaster splitRaster() {
        boolean[][] nav = new boolean[4][1];
        nav[0][0] = true;
        nav[1][0] = true;
        nav[3][0] = true;
        return raster(nav, 4, 1);
    }

    private static LakeNavRaster raster(boolean[][] nav, int width, int height) {
        LakeNavGrid grid = new LakeNavGrid("EPSG:32618", 18, PlanningFixtures.HEAD_LNG, 0, 0, 25, 128, width, height);
        LakePlanningGeometry lake = new LakePlanningGeometry(
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 200),
                List.of());
        LocalMetricCrs.ProjectedGeometry projected = new LocalMetricCrs().project(lake.water(), PlanningFixtures.HEAD_LNG);
        return new LakeNavRaster(grid, nav, new byte[width][height], projected);
    }
}
