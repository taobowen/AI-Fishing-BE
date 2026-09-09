package com.aifishing.planning.spatial;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.planning.spatial.domain.LakeFishingNavEdge;
import com.aifishing.planning.spatial.domain.LakeFishingNavNode;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingTargetSample;
import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.LakeFishingZonePortal;
import com.aifishing.planning.spatial.domain.LakeNavigationTile;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.repo.LakeFishingNavEdgeRepository;
import com.aifishing.planning.spatial.repo.LakeFishingNavNodeRepository;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.LakeFishingTargetSampleRepository;
import com.aifishing.planning.spatial.repo.LakeFishingWaterPathRepository;
import com.aifishing.planning.spatial.repo.LakeFishingZoneMemberRepository;
import com.aifishing.planning.spatial.repo.LakeFishingZonePortalRepository;
import com.aifishing.planning.spatial.repo.LakeFishingZoneRepository;
import com.aifishing.planning.spatial.repo.LakeNavigationTileRepository;
import com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpatialSnapshotService {

    private final PlanningProperties properties;
    private final SpatialPlanningSnapshotRepository snapshotRepository;
    private final LakeFishingTargetRepository targetRepository;
    private final LakeFishingTargetSampleRepository sampleRepository;
    private final LakeFishingZoneRepository zoneRepository;
    private final LakeFishingZoneMemberRepository memberRepository;
    private final LakeFishingZonePortalRepository portalRepository;
    private final LakeFishingNavNodeRepository nodeRepository;
    private final LakeFishingNavEdgeRepository edgeRepository;
    private final LakeFishingWaterPathRepository waterPathRepository;
    private final LakeNavigationTileRepository tileRepository;
    private final LocalMetricCrs localMetricCrs;

    public SpatialSnapshotService(
            PlanningProperties properties,
            SpatialPlanningSnapshotRepository snapshotRepository,
            LakeFishingTargetRepository targetRepository,
            LakeFishingTargetSampleRepository sampleRepository,
            LakeFishingZoneRepository zoneRepository,
            LakeFishingZoneMemberRepository memberRepository,
            LakeFishingZonePortalRepository portalRepository,
            LakeFishingNavNodeRepository nodeRepository,
            LakeFishingNavEdgeRepository edgeRepository,
            LakeFishingWaterPathRepository waterPathRepository,
            LakeNavigationTileRepository tileRepository,
            LocalMetricCrs localMetricCrs
    ) {
        this.properties = properties;
        this.snapshotRepository = snapshotRepository;
        this.targetRepository = targetRepository;
        this.sampleRepository = sampleRepository;
        this.zoneRepository = zoneRepository;
        this.memberRepository = memberRepository;
        this.portalRepository = portalRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.waterPathRepository = waterPathRepository;
        this.tileRepository = tileRepository;
        this.localMetricCrs = localMetricCrs;
    }

    public Optional<SpatialPlanningSnapshot> findReady(
            UUID lakeId,
            Pipeline pipeline,
            String featureAnalysisVersion
    ) {
        PlanningProperties.Spatial spatial = properties.getSpatial();
        return snapshotRepository.findFirstByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersionAndStatus(
                lakeId,
                pipeline,
                featureAnalysisVersion,
                spatial.getDerivationVersion(),
                spatial.getZoneBuilderVersion(),
                spatial.getNavigationVersion(),
                SpatialSnapshotStatus.READY
        );
    }

    public SpatialPlanningSnapshot requireReady(
            UUID lakeId,
            Pipeline pipeline,
            String featureAnalysisVersion
    ) {
        return findReady(lakeId, pipeline, featureAnalysisVersion)
                .orElseThrow(() -> new BadRequestException(
                        "SPATIAL_SNAPSHOT_NOT_READY",
                        "No READY spatial planning snapshot for this lake feature snapshot"));
    }

    @Transactional(readOnly = true)
    public SpatialSnapshotView load(UUID snapshotId) {
        SpatialPlanningSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new BadRequestException("SPATIAL_SNAPSHOT_NOT_READY", "Spatial snapshot not found"));
        if (snapshot.getStatus() != SpatialSnapshotStatus.READY) {
            throw new BadRequestException("SPATIAL_SNAPSHOT_NOT_READY", "Spatial snapshot is not READY");
        }
        return loadTrusted(snapshot);
    }

    SpatialSnapshotView loadTrusted(SpatialPlanningSnapshot snapshot) {
        UUID id = snapshot.getId();
        List<LakeFishingTarget> targets = targetRepository.findBySpatialPlanningSnapshotId(id);
        Map<UUID, List<LakeFishingTargetSample>> samples = new LinkedHashMap<>();
        for (LakeFishingTargetSample sample : sampleRepository.findBySpatialPlanningSnapshotId(id)) {
            samples.computeIfAbsent(sample.getFishingTargetId(), ignored -> new ArrayList<>()).add(sample);
        }
        List<LakeFishingZone> zones = zoneRepository.findBySpatialPlanningSnapshotId(id);
        List<UUID> zoneIds = zones.stream().map(LakeFishingZone::getId).toList();
        Map<UUID, List<LakeFishingZoneMember>> members = new LinkedHashMap<>();
        if (!zoneIds.isEmpty()) {
            for (LakeFishingZoneMember member : memberRepository.findByZoneIdInOrderByZoneIdAscSequenceAsc(zoneIds)) {
                members.computeIfAbsent(member.getZoneId(), ignored -> new ArrayList<>()).add(member);
            }
        }
        Map<UUID, List<VisitPortal>> portals = new LinkedHashMap<>();
        for (LakeFishingZonePortal portal : portalRepository.findBySpatialPlanningSnapshotIdOrderByZoneIdAscSequenceAsc(id)) {
            portals.computeIfAbsent(portal.getZoneId(), ignored -> new ArrayList<>())
                    .add(new VisitPortal(portal.getPortalKey(), portal.getGeom()));
        }
        Map<UUID, ZoneNavGraph> graphs = new LinkedHashMap<>();
        LakeNavRaster raster = loadRaster(snapshot, zones, targets);
        if (raster == null) {
            Map<UUID, List<ZoneNavGraph.Node>> nodes = new HashMap<>();
            for (LakeFishingNavNode node : nodeRepository.findBySpatialPlanningSnapshotId(id)) {
                nodes.computeIfAbsent(node.getZoneId(), ignored -> new ArrayList<>())
                        .add(new ZoneNavGraph.Node(node.getId(), node.getCellX(), node.getCellY(), node.getGeom()));
            }
            Map<UUID, List<ZoneNavGraph.Edge>> edges = new HashMap<>();
            for (LakeFishingNavEdge edge : edgeRepository.findBySpatialPlanningSnapshotId(id)) {
                edges.computeIfAbsent(edge.getZoneId(), ignored -> new ArrayList<>())
                        .add(new ZoneNavGraph.Edge(edge.getFromNodeId(), edge.getToNodeId(), edge.getMeters().doubleValue()));
            }
            for (LakeFishingZone zone : zones) {
                graphs.put(zone.getId(), new ZoneNavGraph(
                        zone.getId(),
                        nodes.getOrDefault(zone.getId(), List.of()),
                        edges.getOrDefault(zone.getId(), List.of())
                ));
            }
        }
        Map<String, SpatialSnapshotView.CachedPath> paths = new ConcurrentHashMap<>();
        for (LakeFishingWaterPath path : waterPathRepository.findBySpatialPlanningSnapshotId(id)) {
            SpatialSnapshotView.CachedPath cached =
                    new SpatialSnapshotView.CachedPath(path.getMeters().doubleValue(), path.getPath());
            paths.put(pathKey(path.getZoneId(), path.getFromKey(), path.getToKey()), cached);
            if (path.getZoneId() == null) {
                paths.put(pathIdentity(path.getNavigationVersion(), path.getFromKey(), path.getToKey()), cached);
            }
        }
        return new SpatialSnapshotView(snapshot, targets, samples, zones, members, portals, graphs, paths, raster);
    }

    private LakeNavRaster loadRaster(
            SpatialPlanningSnapshot snapshot,
            List<LakeFishingZone> zones,
            List<LakeFishingTarget> targets
    ) {
        LakeNavGrid grid = LakeNavGrid.fromMap(snapshot.getNavigationGrid());
        if (grid == null || grid.widthCells() <= 0 || grid.heightCells() <= 0) {
            return null;
        }
        List<LakeNavigationTile> rows = tileRepository.findBySpatialPlanningSnapshotId(snapshot.getId());
        List<LakeNavTiles.Packed> packed = new ArrayList<>();
        for (LakeNavigationTile row : rows) {
            packed.add(new LakeNavTiles.Packed(
                    row.getTileX(), row.getTileY(), row.getTraversabilityMask(), row.getClearanceM()));
        }
        Point ref = referencePoint(zones, targets, grid);
        if (ref == null) {
            return null;
        }
        double lng = grid.referenceLng() != 0 ? grid.referenceLng() : ref.getX();
        return LakeNavTiles.assemble(grid, packed, localMetricCrs.project(ref, lng));
    }

    private static Point referencePoint(List<LakeFishingZone> zones, List<LakeFishingTarget> targets, LakeNavGrid grid) {
        if (zones != null) {
            for (LakeFishingZone zone : zones) {
                if (zone.getRepresentativePoint() != null && !zone.getRepresentativePoint().isEmpty()) {
                    return zone.getRepresentativePoint();
                }
            }
        }
        if (targets != null) {
            for (LakeFishingTarget target : targets) {
                if (target.getRepresentativePoint() != null && !target.getRepresentativePoint().isEmpty()) {
                    return target.getRepresentativePoint();
                }
            }
        }
        return null;
    }

    public static String pathKey(UUID zoneId, String fromKey, String toKey) {
        return zoneId + "|" + fromKey + "|" + toKey;
    }

    public static String pathIdentity(String navigationVersion, String fromKey, String toKey) {
        return (navigationVersion == null ? "" : navigationVersion) + "|" + fromKey + "|" + toKey;
    }
}
