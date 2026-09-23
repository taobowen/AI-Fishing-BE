package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.route.BeamLayerProfile;
import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository.ZonePathKey;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class SnapshotWaterPathService {

    private final LakeFishingWaterPathRepository waterPathRepository;

    public SnapshotWaterPathService(LakeFishingWaterPathRepository waterPathRepository) {
        this.waterPathRepository = waterPathRepository;
    }

    private static void noteWaterLookup() {
        if (!BeamLayerProfile.enabled()) {
            return;
        }
        BeamLayerProfile.noteWaterLookup();
        GenerateProfiler.current().count("waterPathLookups");
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
        return path(view, zoneId, from, to, spatial, null);
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> path(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial,
            PendingZoneWaterPaths pending
    ) {
        if (view == null || zoneId == null || from == null || to == null) {
            return Optional.empty();
        }
        noteWaterLookup();
        long water = BeamLayerProfile.open(BeamLayerProfile.Stage.WATER);
        try {
            if (view.raster() != null) {
                return rasterPath(view, zoneId, from, to, spatial, pending);
            }
            return graphPath(view, zoneId, from, to, spatial, pending);
        } finally {
            BeamLayerProfile.close(water);
        }
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
        noteWaterLookup();
        long water = BeamLayerProfile.open(BeamLayerProfile.Stage.WATER);
        try {
        LakeNavRaster raster = view.raster();
        if (raster.cellOf(from) == null || raster.cellOf(to) == null) {
            return Optional.empty();
        }
        String navigationVersion = view.snapshot() == null ? null : view.snapshot().getNavigationVersion();
        String identity = SpatialSnapshotService.pathIdentity(navigationVersion, fromKey, toKey);
        long lookupStarted = System.nanoTime();
        SpatialSnapshotView.CachedPath cached = cachedSpatial(view, identity, fromKey, toKey);
        if (cached != null) {
            return fromCache(cached, spatial, lookupStarted);
        }
        long lookupNs = System.nanoTime() - lookupStarted;
        long astarStarted = System.nanoTime();
        var computed = raster.shortest(from, to, spatial.getInternalCruiseKmh());
        long astarNs = System.nanoTime() - astarStarted;
        if (computed.isEmpty()) {
            rememberUnreachable(view, identity, fromKey, toKey, lookupNs, astarNs);
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        GenerateProfiler.current().count("waterPathCacheMisses");
        long persistStarted = System.nanoTime();
        persistLazyTransit(view.id(), fromKey, toKey, computed.get(), navigationVersion);
        GenerateProfiler.current().zoneSubPlanner().recordWaterPath(false, lookupNs, astarNs, System.nanoTime() - persistStarted);
        view.waterPaths().put(identity, new SpatialSnapshotView.CachedPath(computed.get().meters(), computed.get().path()));
        return computed;
        } finally {
            BeamLayerProfile.close(water);
        }
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> rasterPath(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial,
            PendingZoneWaterPaths pending
    ) {
        LakeNavRaster raster = view.raster();
        String fromKey = LakeNavRaster.cellKey(raster.cellOf(from));
        String toKey = LakeNavRaster.cellKey(raster.cellOf(to));
        if (fromKey.isBlank() || toKey.isBlank()) {
            return Optional.empty();
        }
        String key = SpatialSnapshotService.pathKey(zoneId, fromKey, toKey);
        long lookupStarted = System.nanoTime();
        SpatialSnapshotView.CachedPath cached = cachedSpatial(view, key, fromKey, toKey);
        if (cached != null) {
            return fromCache(cached, spatial, lookupStarted);
        }
        long lookupNs = System.nanoTime() - lookupStarted;
        long astarStarted = System.nanoTime();
        var computed = raster.shortest(from, to, spatial.getInternalCruiseKmh());
        long astarNs = System.nanoTime() - astarStarted;
        if (computed.isEmpty()) {
            rememberUnreachable(view, key, fromKey, toKey, lookupNs, astarNs);
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        GenerateProfiler.current().count("waterPathCacheMisses");
        rememberZonePath(
                view, zoneId, fromKey, toKey, computed.get(), view.snapshot().getNavigationVersion(), pending, lookupNs, astarNs);
        return computed;
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> graphPath(
            SpatialSnapshotView view,
            UUID zoneId,
            Point from,
            Point to,
            PlanningProperties.Spatial spatial,
            PendingZoneWaterPaths pending
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
        long lookupStarted = System.nanoTime();
        SpatialSnapshotView.CachedPath cached = view.waterPaths().get(key);
        if (cached != null) {
            return fromCache(cached, spatial, lookupStarted);
        }
        long lookupNs = System.nanoTime() - lookupStarted;
        long astarStarted = System.nanoTime();
        var computed = graph.shortest(from, to, spatial.getInternalCruiseKmh());
        long astarNs = System.nanoTime() - astarStarted;
        if (computed.isEmpty()) {
            rememberUnreachable(view, key, null, null, lookupNs, astarNs);
            return Optional.empty();
        }
        GenerateProfiler.current().count("lazyPathCalculations");
        GenerateProfiler.current().count("waterPathCacheMisses");
        rememberZonePath(
                view,
                zoneId,
                fromNode.toString(),
                toNode.toString(),
                computed.get(),
                view.snapshot().getNavigationVersion(),
                pending,
                lookupNs,
                astarNs);
        return computed;
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> fromCache(
            SpatialSnapshotView.CachedPath cached,
            PlanningProperties.Spatial spatial,
            long lookupStarted
    ) {
        GenerateProfiler.current().count("astarCacheHits");
        GenerateProfiler.current().count("waterPathCacheHits");
        GenerateProfiler.current().zoneSubPlanner().recordWaterPath(true, System.nanoTime() - lookupStarted, 0, 0);
        if (cached.isUnreachable()) {
            return Optional.empty();
        }
        double minutes = cached.meters() / 1000.0 / Math.max(0.8, spatial.getInternalCruiseKmh()) * 60.0;
        return Optional.of(new LocalWaterPathEstimator.PathEstimate(cached.geometry(), cached.meters(), minutes));
    }

    private SpatialSnapshotView.CachedPath cachedSpatial(
            SpatialSnapshotView view,
            String key,
            String fromKey,
            String toKey
    ) {
        SpatialSnapshotView.CachedPath cached = view.waterPaths().get(key);
        if (cached != null) {
            return cached;
        }
        return view.waterPaths().get(spatialCellKey(fromKey, toKey));
    }

    private static String spatialCellKey(String fromKey, String toKey) {
        return "cells|" + fromKey + "|" + toKey;
    }

    private void rememberUnreachable(
            SpatialSnapshotView view,
            String key,
            String fromKey,
            String toKey,
            long lookupNs,
            long astarNs
    ) {
        SpatialSnapshotView.CachedPath unreachable = SpatialSnapshotView.CachedPath.unreachable();
        view.waterPaths().put(key, unreachable);
        if (fromKey != null && toKey != null) {
            view.waterPaths().put(spatialCellKey(fromKey, toKey), unreachable);
        }
        GenerateProfiler.current().zoneSubPlanner().recordWaterPath(false, lookupNs, astarNs, 0);
    }

    private void rememberZonePath(
            SpatialSnapshotView view,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            String navigationVersion,
            PendingZoneWaterPaths pending,
            long lookupNs,
            long astarNs
    ) {
        String key = SpatialSnapshotService.pathKey(zoneId, fromKey, toKey);
        SpatialSnapshotView.CachedPath cached = new SpatialSnapshotView.CachedPath(estimate.meters(), estimate.path());
        view.waterPaths().put(key, cached);
        if (pending != null) {
            pending.add(view.id(), zoneId, fromKey, toKey, estimate, navigationVersion);
            GenerateProfiler.current().zoneSubPlanner().recordWaterPath(false, lookupNs, astarNs, 0);
            return;
        }
        long persistStarted = System.nanoTime();
        persistLazy(view.id(), zoneId, fromKey, toKey, estimate, navigationVersion);
        GenerateProfiler.current().zoneSubPlanner().recordWaterPath(false, lookupNs, astarNs, System.nanoTime() - persistStarted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushPending(PendingZoneWaterPaths pending) {
        if (pending == null || waterPathRepository == null) {
            return;
        }
        List<PendingZoneWaterPaths.Row> rows = pending.drain();
        if (rows.isEmpty()) {
            return;
        }
        try {
            List<LakeFishingWaterPath> fresh = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            UUID snapshotId = null;
            Set<String> existing = Set.of();
            for (PendingZoneWaterPaths.Row row : rows) {
                if (snapshotId == null || !snapshotId.equals(row.snapshotId())) {
                    snapshotId = row.snapshotId();
                    existing = new HashSet<>();
                    for (ZonePathKey key : waterPathRepository.findZonePathKeys(snapshotId)) {
                        existing.add(PendingZoneWaterPaths.key(snapshotId, key.getZoneId(), key.getFromKey(), key.getToKey()));
                    }
                }
                String identity = PendingZoneWaterPaths.key(row.snapshotId(), row.zoneId(), row.fromKey(), row.toKey());
                if (!seen.add(identity) || existing.contains(identity)) {
                    continue;
                }
                fresh.add(row.toEntity());
            }
            if (!fresh.isEmpty()) {
                waterPathRepository.saveAll(fresh);
            }
        } catch (RuntimeException ex) {
            for (PendingZoneWaterPaths.Row row : rows) {
                pending.add(row.snapshotId(), row.zoneId(), row.fromKey(), row.toKey(), row.estimate(), row.navigationVersion());
            }
            throw ex;
        }
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
