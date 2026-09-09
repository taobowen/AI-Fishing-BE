package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class SnapshotWaterPathService {

    private final LakeFishingWaterPathRepository waterPathRepository;

    public SnapshotWaterPathService(LakeFishingWaterPathRepository waterPathRepository) {
        this.waterPathRepository = waterPathRepository;
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> path(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial
    ) {
        if (view == null || zoneId == null || from == null || to == null) {
            return Optional.empty();
        }
        if (view.raster() != null) {
            return rasterPath(view, zoneId, from, to, spatial);
        }
        return graphPath(view, zoneId, from, to, spatial);
    }

    /**
     * Lake-wide transit lookup. Cache identity is snapshot + navigationVersion + fromKey + toKey.
     * Does not invent a zoneId. Snaps endpoints to traversable cells before A*.
     */
    public Optional<LocalWaterPathEstimator.PathEstimate> transitPath(
            SpatialSnapshotView view,
            String fromKey,
            String toKey,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial
    ) {
        if (view == null || view.raster() == null || from == null || to == null
                || fromKey == null || toKey == null || fromKey.isBlank() || toKey.isBlank()) {
            return Optional.empty();
        }
        LakeNavRaster raster = view.raster();
        if (raster.cellOf(from) == null || raster.cellOf(to) == null) {
            return Optional.empty();
        }
        String navigationVersion = view.snapshot() == null ? null : view.snapshot().getNavigationVersion();
        String identity = SpatialSnapshotService.pathIdentity(navigationVersion, fromKey, toKey);
        SpatialSnapshotView.CachedPath cached = view.waterPaths().get(identity);
        if (cached != null) {
            GenerateProfiler.current().count("astarCacheHits");
            double minutes = cached.meters() / 1000.0 / Math.max(0.8, spatial.getInternalCruiseKmh()) * 60.0;
            return Optional.of(new LocalWaterPathEstimator.PathEstimate(cached.geometry(), cached.meters(), minutes));
        }
        var computed = raster.shortest(from, to, spatial.getInternalCruiseKmh());
        if (computed.isEmpty()) {
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        persistLazyTransit(view.id(), fromKey, toKey, computed.get(), navigationVersion);
        view.waterPaths().put(identity, new SpatialSnapshotView.CachedPath(computed.get().meters(), computed.get().path()));
        return computed;
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> rasterPath(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial
    ) {
        LakeNavRaster raster = view.raster();
        String fromKey = LakeNavRaster.cellKey(raster.cellOf(from));
        String toKey = LakeNavRaster.cellKey(raster.cellOf(to));
        if (fromKey.isBlank() || toKey.isBlank()) {
            return Optional.empty();
        }
        String key = SpatialSnapshotService.pathKey(zoneId, fromKey, toKey);
        SpatialSnapshotView.CachedPath cached = view.waterPaths().get(key);
        if (cached != null) {
            GenerateProfiler.current().count("astarCacheHits");
            double minutes = cached.meters() / 1000.0 / Math.max(0.8, spatial.getInternalCruiseKmh()) * 60.0;
            return Optional.of(new LocalWaterPathEstimator.PathEstimate(cached.geometry(), cached.meters(), minutes));
        }
        var computed = raster.shortest(from, to, spatial.getInternalCruiseKmh());
        if (computed.isEmpty()) {
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        persistLazy(view.id(), zoneId, fromKey, toKey, computed.get(), view.snapshot().getNavigationVersion());
        view.waterPaths().put(key, new SpatialSnapshotView.CachedPath(computed.get().meters(), computed.get().path()));
        return computed;
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> graphPath(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial
    ) {
        ZoneNavGraph graph = view.graphsByZone().get(zoneId);
        if (graph == null) {
            GenerateProfiler.current().count("graphRebuild");
            return Optional.empty();
        }
        UUID fromNode = graph.nearestNodeId(from);
        UUID toNode = graph.nearestNodeId(to);
        if (fromNode == null || toNode == null) {
            return Optional.empty();
        }
        String key = SpatialSnapshotService.pathKey(zoneId, fromNode.toString(), toNode.toString());
        SpatialSnapshotView.CachedPath cached = view.waterPaths().get(key);
        if (cached != null) {
            GenerateProfiler.current().count("astarCacheHits");
            double minutes = cached.meters() / 1000.0 / Math.max(0.8, spatial.getInternalCruiseKmh()) * 60.0;
            return Optional.of(new LocalWaterPathEstimator.PathEstimate(cached.geometry(), cached.meters(), minutes));
        }
        var computed = graph.shortest(from, to, spatial.getInternalCruiseKmh());
        if (computed.isEmpty()) {
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        persistLazy(view.id(), zoneId, fromNode.toString(), toNode.toString(), computed.get(), view.snapshot().getNavigationVersion());
        view.waterPaths().put(key, new SpatialSnapshotView.CachedPath(computed.get().meters(), computed.get().path()));
        return computed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLazy(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate
    ) {
        persistLazy(snapshotId, zoneId, fromKey, toKey, estimate, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLazy(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            String navigationVersion
    ) {
        if (waterPathRepository == null) {
            return;
        }
        if (waterPathRepository.findBySpatialPlanningSnapshotIdAndZoneIdAndFromKeyAndToKey(
                snapshotId, zoneId, fromKey, toKey).isPresent()) {
            return;
        }
        LakeFishingWaterPath row = new LakeFishingWaterPath();
        row.setSpatialPlanningSnapshotId(snapshotId);
        row.setZoneId(zoneId);
        row.setFromKey(fromKey);
        row.setToKey(toKey);
        row.setMeters(BigDecimal.valueOf(estimate.meters()));
        row.setPath(estimate.path());
        row.setPrecomputed(false);
        row.setNavigationVersion(navigationVersion);
        waterPathRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLazyTransit(
            UUID snapshotId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            String navigationVersion
    ) {
        if (waterPathRepository == null) {
            return;
        }
        if (waterPathRepository.findFirstBySpatialPlanningSnapshotIdAndFromKeyAndToKeyAndZoneIdIsNull(
                snapshotId, fromKey, toKey).isPresent()) {
            return;
        }
        LakeFishingWaterPath row = new LakeFishingWaterPath();
        row.setSpatialPlanningSnapshotId(snapshotId);
        row.setZoneId(null);
        row.setFromKey(fromKey);
        row.setToKey(toKey);
        row.setMeters(BigDecimal.valueOf(estimate.meters()));
        row.setPath(estimate.path());
        row.setPrecomputed(false);
        row.setNavigationVersion(navigationVersion);
        waterPathRepository.save(row);
    }

    public Optional<UUID> findTransitPathId(UUID snapshotId, String fromKey, String toKey) {
        if (waterPathRepository == null || snapshotId == null) {
            return Optional.empty();
        }
        return waterPathRepository
                .findFirstBySpatialPlanningSnapshotIdAndFromKeyAndToKeyAndZoneIdIsNull(snapshotId, fromKey, toKey)
                .map(LakeFishingWaterPath::getId);
    }
}
