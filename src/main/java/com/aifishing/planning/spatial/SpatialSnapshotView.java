package com.aifishing.planning.spatial;

import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingTargetSample;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import org.locationtech.jts.geom.LineString;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record SpatialSnapshotView(
        SpatialPlanningSnapshot snapshot,
        List<LakeFishingTarget> targets,
        Map<UUID, List<LakeFishingTargetSample>> samplesByTarget,
        List<LakeFishingZone> zones,
        Map<UUID, List<LakeFishingZoneMember>> membersByZone,
        Map<UUID, List<VisitPortal>> portalsByZone,
        Map<UUID, ZoneNavGraph> graphsByZone,
        Map<String, CachedPath> waterPaths,
        LakeNavRaster raster
) {
    public SpatialSnapshotView {
        targets = targets == null ? List.of() : List.copyOf(targets);
        samplesByTarget = samplesByTarget == null ? Map.of() : Map.copyOf(samplesByTarget);
        zones = zones == null ? List.of() : List.copyOf(zones);
        membersByZone = membersByZone == null ? Map.of() : Map.copyOf(membersByZone);
        portalsByZone = portalsByZone == null ? Map.of() : Map.copyOf(portalsByZone);
        graphsByZone = graphsByZone == null ? Map.of() : Map.copyOf(graphsByZone);
        waterPaths = waterPaths == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(waterPaths);
    }

    public SpatialSnapshotView(
            SpatialPlanningSnapshot snapshot,
            List<LakeFishingTarget> targets,
            Map<UUID, List<LakeFishingTargetSample>> samplesByTarget,
            List<LakeFishingZone> zones,
            Map<UUID, List<LakeFishingZoneMember>> membersByZone,
            Map<UUID, List<VisitPortal>> portalsByZone,
            Map<UUID, ZoneNavGraph> graphsByZone,
            Map<String, CachedPath> waterPaths
    ) {
        this(snapshot, targets, samplesByTarget, zones, membersByZone, portalsByZone, graphsByZone, waterPaths, null);
    }

    public UUID id() {
        return snapshot.getId();
    }

    public record CachedPath(double meters, LineString geometry) {
    }
}
