package com.aifishing.planning.spatial;

import com.aifishing.common.geo.WaterDepth;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateLocationService;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingTargetSample;
import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.LakeFishingZonePortal;
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
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class SpatialSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(SpatialSnapshotJob.class);
    private static final int TILE_JDBC_BATCH = 64;

    private final PlanningProperties properties;
    private final LakeRepository lakeRepository;
    private final LakeFeatureRepository featureRepository;
    private final LakePlanningGeometryLoader geometryLoader;
    private final CandidateLocationService locationService;
    private final FishingTargetBuilder targetBuilder;
    private final FishingZoneBuilder zoneBuilder;
    private final LakeNavRasterBuilder rasterBuilder;
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
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public SpatialSnapshotJob(
            PlanningProperties properties,
            LakeRepository lakeRepository,
            LakeFeatureRepository featureRepository,
            LakePlanningGeometryLoader geometryLoader,
            CandidateLocationService locationService,
            FishingTargetBuilder targetBuilder,
            FishingZoneBuilder zoneBuilder,
            LakeNavRasterBuilder rasterBuilder,
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
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Qualifier("spatialSnapshotExecutor") Executor executor
    ) {
        this.properties = properties;
        this.lakeRepository = lakeRepository;
        this.featureRepository = featureRepository;
        this.geometryLoader = geometryLoader;
        this.locationService = locationService;
        this.targetBuilder = targetBuilder;
        this.zoneBuilder = zoneBuilder;
        this.rasterBuilder = rasterBuilder;
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
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    public SpatialPlanningSnapshot buildIfReady(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        if (pipeline == Pipeline.VISION) {
            return null;
        }
        long features = featureRepository.countByLakeIdAndPipelineAndAnalysisVersion(lakeId, pipeline, analysisVersion);
        if (features <= 0) {
            return null;
        }
        return build(lakeId, pipeline, analysisVersion);
    }

    public SpatialPlanningSnapshot submitIfReady(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        if (pipeline == Pipeline.VISION) {
            return null;
        }
        long features = featureRepository.countByLakeIdAndPipelineAndAnalysisVersion(lakeId, pipeline, analysisVersion);
        if (features <= 0) {
            return null;
        }
        return submit(lakeId, pipeline, analysisVersion);
    }

    public SpatialPlanningSnapshot submit(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        SpatialPlanningSnapshot started = transactionTemplate.execute(
                status -> ensureRunning(lakeId, pipeline, analysisVersion, properties.getSpatial()));
        if (started == null) {
            throw new IllegalStateException("Spatial snapshot submit returned null");
        }
        if (started.getStatus() == SpatialSnapshotStatus.READY) {
            return started;
        }
        UUID id = started.getId();
        if (!inFlight.add(id)) {
            return started;
        }
        executor.execute(() -> {
            Object lock = lockFor(lakeId, pipeline, analysisVersion);
            synchronized (lock) {
                try {
                    completeBuild(id);
                } catch (RuntimeException ex) {
                    log.warn("Spatial snapshot {} failed: {}", id, ex.getMessage());
                    transactionTemplate.execute(status -> markFailed(id, ex.getMessage()));
                } finally {
                    inFlight.remove(id);
                }
            }
        });
        return started;
    }

    public SpatialPlanningSnapshot build(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        Object lock = lockFor(lakeId, pipeline, analysisVersion);
        synchronized (lock) {
            SpatialPlanningSnapshot started = transactionTemplate.execute(
                    status -> ensureRunning(lakeId, pipeline, analysisVersion, properties.getSpatial()));
            if (started == null) {
                throw new IllegalStateException("Spatial snapshot build returned null");
            }
            if (started.getStatus() == SpatialSnapshotStatus.READY) {
                return started;
            }
            inFlight.add(started.getId());
            try {
                return completeBuild(started.getId());
            } catch (RuntimeException ex) {
                transactionTemplate.execute(status -> markFailed(started.getId(), ex.getMessage()));
                throw ex;
            } finally {
                inFlight.remove(started.getId());
            }
        }
    }

    private Object lockFor(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        PlanningProperties.Spatial spatial = properties.getSpatial();
        String key = "spatial-snap:" + lakeId + ":" + pipeline + ":" + analysisVersion + ":"
                + spatial.getDerivationVersion() + ":" + spatial.getZoneBuilderVersion() + ":"
                + spatial.getNavigationVersion();
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private SpatialPlanningSnapshot ensureRunning(
            UUID lakeId,
            Pipeline pipeline,
            String analysisVersion,
            PlanningProperties.Spatial spatial
    ) {
        var existing = snapshotRepository
                .findByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersion(
                        lakeId,
                        pipeline,
                        analysisVersion,
                        spatial.getDerivationVersion(),
                        spatial.getZoneBuilderVersion(),
                        spatial.getNavigationVersion()
                );
        if (existing.isPresent() && existing.get().getStatus() == SpatialSnapshotStatus.READY) {
            return existing.get();
        }
        SpatialPlanningSnapshot snapshot = existing.orElseGet(() -> newSnapshot(lakeId, pipeline, analysisVersion, spatial));
        if (snapshot.getId() != null && snapshot.getStatus() == SpatialSnapshotStatus.FAILED) {
            deleteProducts(snapshot.getId());
        }
        snapshot.setStatus(SpatialSnapshotStatus.RUNNING);
        snapshot.setErrorMessage(null);
        snapshot.setStartedAt(Instant.now());
        snapshot.setCompletedAt(null);
        Map<String, Object> counts = snapshot.getCounts() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.getCounts());
        counts.put("stage", "STARTED");
        snapshot.setCounts(counts);
        try {
            return snapshotRepository.saveAndFlush(snapshot);
        } catch (DataIntegrityViolationException ex) {
            return snapshotRepository
                    .findByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersion(
                            lakeId,
                            pipeline,
                            analysisVersion,
                            spatial.getDerivationVersion(),
                            spatial.getZoneBuilderVersion(),
                            spatial.getNavigationVersion()
                    )
                    .orElseThrow();
        }
    }

    private SpatialPlanningSnapshot markFailed(UUID snapshotId, String message) {
        SpatialPlanningSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
        snapshot.setStatus(SpatialSnapshotStatus.FAILED);
        snapshot.setCompletedAt(Instant.now());
        snapshot.setErrorMessage(truncate(message));
        Map<String, Object> counts = snapshot.getCounts() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.getCounts());
        counts.put("stage", "FAILED");
        snapshot.setCounts(counts);
        GenerateProfiler.clear();
        return snapshotRepository.save(snapshot);
    }

    private SpatialPlanningSnapshot completeBuild(UUID snapshotId) {
        SpatialPlanningSnapshot current = snapshotRepository.findById(snapshotId).orElseThrow();
        if (current.getStatus() == SpatialSnapshotStatus.READY) {
            return current;
        }
        transactionTemplate.execute(status -> {
            deleteProducts(snapshotId);
            return null;
        });
        PlanningProperties.Spatial spatial = properties.getSpatial();
        GenerateProfiler profiler = GenerateProfiler.begin();
        UUID lakeId = current.getLakeId();
        Pipeline pipeline = current.getFeaturePipeline();
        String analysisVersion = current.getFeatureAnalysisVersion();
        var lake = lakeRepository.findById(lakeId).orElseThrow();
        LakePlanningGeometry geometry = geometryLoader.load(lake);
        List<LakeFeature> features = featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(
                lakeId, pipeline, analysisVersion);
        profiler.count("lakeFeatureCount", features.size());

        profiler.start(GenerateProfiler.WATER_PATH_BUILD);
        LakeNavRaster raster = rasterBuilder.build(geometry, spatial);
        profiler.end(GenerateProfiler.WATER_PATH_BUILD);
        transactionTemplate.execute(status -> {
            persistTiles(snapshotId, raster);
            SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
            snap.setNavigationGrid(raster.grid().toMap());
            updateStage(snap, "RASTER", Map.of(
                    "navigableCellCount", raster.navigableCellCount(),
                    "rasterTileCount", raster.packTiles().size(),
                    "stage", "RASTER"
            ));
            return snapshotRepository.saveAndFlush(snap);
        });

        SpatialSkipLedger ledger = new SpatialSkipLedger();
        List<CandidateSpot> seeds = new ArrayList<>();
        for (LakeFeature feature : features) {
            var located = locationService.locate(feature, geometry);
            if (located.isEmpty()) {
                continue;
            }
            seeds.add(toSeed(feature, located.get()));
        }
        List<CandidateSpot> atomics = targetBuilder.enrich(seeds, geometry, properties, snapshotId, ledger);
        transactionTemplate.execute(status -> {
            SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
            int persisted = 0;
            for (CandidateSpot spot : atomics) {
                persistTarget(spot, snap, pipeline, analysisVersion, spatial);
                persistSamples(spot, snapshotId);
                if (++persisted % 200 == 0) {
                    flushPersistence();
                }
            }
            flushPersistence();
            updateStage(snap, "TARGETS", Map.of(
                    "lakeFeatureCount", features.size(),
                    "generatedTargetCount", atomics.size(),
                    "targetsBuilt", atomics.size(),
                    "stage", "TARGETS"
            ));
            return snapshotRepository.saveAndFlush(snap);
        });

        log.info("Spatial snapshot {} clustering {} targets", snapshotId, atomics.size());
        long clusterStarted = System.currentTimeMillis();
        List<CandidateSpot> zones = zoneBuilder.cluster(atomics, geometry, properties, ledger, raster);
        long clusterMs = System.currentTimeMillis() - clusterStarted;
        log.info("Spatial snapshot {} clustered {} targets into {} zones in {} ms",
                snapshotId, atomics.size(), zones.size(), clusterMs);
        ClusterStats clusterStats = zoneBuilder.lastStats();

        if (ledger.exceedsThreshold(atomics.size(), spatial)) {
            return transactionTemplate.execute(status -> {
                SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
                Map<String, Object> counts = baseCounts(features.size(), atomics.size(), zones.size(), ledger, clusterStats, raster);
                counts.putAll(ledger.toCounts(atomics.size()));
                counts.put("stage", "FAILED");
                snap.setCounts(counts);
                snap.setTimings(profiler.snapshot());
                snap.setStatus(SpatialSnapshotStatus.FAILED);
                snap.setCompletedAt(Instant.now());
                snap.setErrorMessage(truncate(ledger.failureMessage(atomics.size(), spatial)));
                GenerateProfiler.clear();
                return snapshotRepository.save(snap);
            });
        }

        transactionTemplate.execute(status -> {
            SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
            int zoneIndex = 0;
            for (CandidateSpot zone : zones) {
                zoneIndex++;
                persistZone(zone, snap, pipeline, analysisVersion, spatial);
                if (zoneIndex % 25 == 0) {
                    flushPersistence();
                }
            }
            flushPersistence();
            Map<String, Object> counts = baseCounts(features.size(), atomics.size(), zones.size(), ledger, clusterStats, raster);
            counts.put("zonesProcessed", zones.size());
            counts.put("zonesTotal", zones.size());
            counts.put("stage", "ZONES");
            snap.setCounts(counts);
            return snapshotRepository.saveAndFlush(snap);
        });

        int[] pathCount = {0};
        profiler.start(GenerateProfiler.WATER_PATH_BUILD);
        transactionTemplate.execute(status -> {
            SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
            for (CandidateSpot zone : zones) {
                pathCount[0] += precomputeUsefulPairs(snapshotId, zone, raster, spatial);
            }
            flushPersistence();
            Map<String, Object> counts = baseCounts(features.size(), atomics.size(), zones.size(), ledger, clusterStats, raster);
            counts.put("precomputedPairCount", pathCount[0]);
            counts.put("waterPathsBuilt", pathCount[0]);
            counts.put("stage", "PATHS");
            snap.setCounts(counts);
            return snapshotRepository.saveAndFlush(snap);
        });
        profiler.end(GenerateProfiler.WATER_PATH_BUILD);

        return transactionTemplate.execute(status -> {
            validate(snapshotId, atomics, zones);
            SpatialPlanningSnapshot snap = snapshotRepository.findById(snapshotId).orElseThrow();
            Map<String, Object> counts = baseCounts(features.size(), atomics.size(), zones.size(), ledger, clusterStats, raster);
            counts.put("navigationNodeCount", raster.navigableCellCount());
            counts.put("navigationEdgeCount", 0);
            counts.put("precomputedPairCount", pathCount[0]);
            counts.put("waterPathsBuilt", pathCount[0]);
            counts.put("theoreticalFullPairCount", clusterStats.theoreticalPairCount());
            counts.put("lazyPathCalculations", 0);
            counts.put("stage", "READY");
            snap.setCounts(counts);
            snap.setTimings(profiler.snapshot());
            snap.setStatus(SpatialSnapshotStatus.READY);
            snap.setCompletedAt(Instant.now());
            snap.setErrorMessage(null);
            GenerateProfiler.clear();
            return snapshotRepository.save(snap);
        });
    }

    private static Map<String, Object> baseCounts(
            int features,
            int targets,
            int zones,
            SpatialSkipLedger ledger,
            ClusterStats clusterStats,
            LakeNavRaster raster
    ) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("lakeFeatureCount", features);
        counts.put("generatedTargetCount", targets);
        counts.put("targetsBuilt", targets);
        counts.put("physicalZoneCount", zones);
        counts.put("zonesProcessed", zones);
        counts.put("zonesTotal", zones);
        counts.put("navigableCellCount", raster.navigableCellCount());
        counts.put("rasterTileCount", raster.packTiles().size());
        counts.put("navigationEdgeCount", 0);
        counts.putAll(ledger.toCounts(targets));
        counts.putAll(clusterStats.toCounts());
        return counts;
    }

    private void updateStage(SpatialPlanningSnapshot snap, String stage, Map<String, Object> extra) {
        Map<String, Object> counts = snap.getCounts() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snap.getCounts());
        counts.putAll(extra);
        counts.put("stage", stage);
        snap.setCounts(counts);
    }

    private SpatialPlanningSnapshot newSnapshot(
            UUID lakeId,
            Pipeline pipeline,
            String analysisVersion,
            PlanningProperties.Spatial spatial
    ) {
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setLakeId(lakeId);
        snapshot.setFeaturePipeline(pipeline);
        snapshot.setFeatureAnalysisVersion(analysisVersion);
        snapshot.setTargetDerivationVersion(spatial.getDerivationVersion());
        snapshot.setZoneBuilderVersion(spatial.getZoneBuilderVersion());
        snapshot.setNavigationVersion(spatial.getNavigationVersion());
        snapshot.setStatus(SpatialSnapshotStatus.RUNNING);
        snapshot.setStartedAt(Instant.now());
        return snapshot;
    }

    private void deleteProducts(UUID snapshotId) {
        waterPathRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        tileRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        edgeRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        nodeRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        portalRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        sampleRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        for (LakeFishingZone zone : zoneRepository.findBySpatialPlanningSnapshotId(snapshotId)) {
            memberRepository.deleteAll(memberRepository.findByZoneIdOrderBySequenceAsc(zone.getId()));
        }
        zoneRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
        targetRepository.deleteBySpatialPlanningSnapshotId(snapshotId);
    }

    private CandidateSpot toSeed(LakeFeature feature, org.locationtech.jts.geom.Point location) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(feature.getId());
        spot.setType(feature.getType());
        spot.setSourceGeometry(feature.getGeometry());
        spot.setLocation(location);
        Double minDepth = WaterDepth.meters(feature.getMinDepthM() == null ? null : feature.getMinDepthM().doubleValue());
        Double maxDepth = WaterDepth.meters(feature.getMaxDepthM() == null ? null : feature.getMaxDepthM().doubleValue());
        spot.setMinDepthM(minDepth);
        spot.setMaxDepthM(maxDepth);
        if (minDepth != null && maxDepth != null) {
            spot.setRepresentativeDepthM((minDepth + maxDepth) / 2.0);
        } else if (minDepth != null) {
            spot.setRepresentativeDepthM(minDepth);
        } else {
            spot.setRepresentativeDepthM(maxDepth);
        }
        spot.setFeatureConfidence(feature.getConfidence() == null ? null : feature.getConfidence().doubleValue());
        spot.setAnalysisVersion(feature.getAnalysisVersion());
        spot.setPipeline(feature.getPipeline());
        if (feature.getOrientation() != null) {
            spot.setRawOrientationDeg(feature.getOrientation().doubleValue());
        }
        return spot;
    }

    private void persistTarget(
            CandidateSpot spot,
            SpatialPlanningSnapshot snapshot,
            Pipeline pipeline,
            String analysisVersion,
            PlanningProperties.Spatial spatial
    ) {
        if (spot.getTargetKind() == null
                || spot.getTargetKind() == TargetKind.ZONE
                || !spot.getTargetKind().allowedOnLakeFishingTargets()) {
            return;
        }
        TargetKind kind = spot.getTargetKind() == TargetKind.POINT ? TargetKind.POINT : TargetKind.PATH;
        LakeFishingTarget row = new LakeFishingTarget();
        row.setId(spot.getFishingTargetId());
        row.setSpatialPlanningSnapshotId(snapshot.getId());
        row.setLakeId(snapshot.getLakeId());
        row.setFeaturePipeline(spot.getPipeline() == null ? pipeline : spot.getPipeline());
        row.setFeatureAnalysisVersion(spot.getAnalysisVersion() == null ? analysisVersion : spot.getAnalysisVersion());
        row.setDerivationVersion(spatial.getDerivationVersion());
        row.setTargetKind(kind);
        row.setSemanticType(spot.getType());
        row.setGeometry(spot.getTargetGeometry());
        row.setRepresentativePoint(spot.getLocation() != null ? spot.getLocation() : spot.getEntryPoint());
        row.setFishingCorridor(spot.getFishingCorridor());
        row.setEntryPoint(spot.getEntryPoint());
        row.setExitPoint(spot.getExitPoint());
        if (spot.getSelectedFishingPath() instanceof LineString lineString) {
            row.setSelectedFishingPath(lineString);
        }
        if (spot.getFishingCorridorWidthM() != null) {
            row.setFishingCorridorWidthM(BigDecimal.valueOf(spot.getFishingCorridorWidthM()));
        }
        row.setSourceFeatureIds(spot.getFeatureId() == null ? List.of() : List.of(spot.getFeatureId()));
        row.setSegmentIndex(0);
        row.setClosedLoop(spot.isClosedLoop());
        row.setPathTopology(spot.getPathTopology());
        if (spot.getChainageStartM() != null) {
            row.setChainageStartM(BigDecimal.valueOf(spot.getChainageStartM()));
        }
        if (spot.getChainageEndM() != null) {
            row.setChainageEndM(BigDecimal.valueOf(spot.getChainageEndM()));
        }
        row.setSplitReason(spot.getSplitReason());
        if (spot.getMinDepthM() != null) {
            row.setMinDepthM(BigDecimal.valueOf(spot.getMinDepthM()));
        }
        if (spot.getMaxDepthM() != null) {
            row.setMaxDepthM(BigDecimal.valueOf(spot.getMaxDepthM()));
        }
        if (spot.getRepresentativeDepthM() != null) {
            row.setRepresentativeDepthM(BigDecimal.valueOf(spot.getRepresentativeDepthM()));
        }
        if (spot.getFeatureConfidence() != null) {
            row.setConfidence(BigDecimal.valueOf(spot.getFeatureConfidence()));
        }
        row.setStaticMetadata(Map.of(
                "splitReason", spot.getSplitReason() == null ? "" : spot.getSplitReason(),
                "closedLoop", spot.isClosedLoop()
        ));
        if (row.getGeometry() == null || row.getRepresentativePoint() == null) {
            return;
        }
        targetRepository.save(row);
    }

    private void persistSamples(CandidateSpot spot, UUID snapshotId) {
        if (spot.getFishingTargetId() == null || spot.getStaticSamples() == null) {
            return;
        }
        int i = 0;
        for (SpatialUtility.Sample sample : spot.getStaticSamples()) {
            LakeFishingTargetSample row = new LakeFishingTargetSample();
            row.setSpatialPlanningSnapshotId(snapshotId);
            row.setFishingTargetId(spot.getFishingTargetId());
            row.setFraction(BigDecimal.valueOf(sample.fraction()));
            row.setGeom(sample.point());
            sampleRepository.save(row);
            i++;
            if (i > 24) {
                break;
            }
        }
    }

    private void persistZone(
            CandidateSpot zone,
            SpatialPlanningSnapshot snapshot,
            Pipeline pipeline,
            String analysisVersion,
            PlanningProperties.Spatial spatial
    ) {
        LakeFishingZone row = new LakeFishingZone();
        row.setId(zone.getZoneId());
        row.setSpatialPlanningSnapshotId(snapshot.getId());
        row.setLakeId(snapshot.getLakeId());
        row.setFeaturePipeline(pipeline);
        row.setFeatureAnalysisVersion(analysisVersion);
        row.setBuilderVersion(spatial.getZoneBuilderVersion());
        row.setNavigationVersion(spatial.getNavigationVersion());
        row.setGeometry(zone.getTargetGeometry());
        row.setRepresentativePoint(zone.getLocation());
        row.setClusteringParams(Map.of(
                "neighborSearchRadiusM", spatial.getZoneNeighborSearchRadiusM(),
                "waterPathJoinMaxM", spatial.getZoneWaterPathJoinMaxM(),
                "maxWaterPathDiameterM", spatial.getZoneMaxWaterPathDiameterM(),
                "maxInternalGapM", spatial.getZoneMaxInternalGapM(),
                "minCoverageDensity", spatial.getZoneMinCoverageDensity(),
                "strategyNeutral", true
        ));
        zoneRepository.save(row);
        int sequence = 1;
        for (CandidateSpot member : zone.getZoneMembers()) {
            if (member.getFishingTargetId() == null) {
                continue;
            }
            LakeFishingZoneMember link = new LakeFishingZoneMember();
            link.setZoneId(row.getId());
            link.setFishingTargetId(member.getFishingTargetId());
            link.setSequence(sequence++);
            memberRepository.save(link);
        }
        int portalSeq = 0;
        for (VisitPortal portal : zone.getPortals()) {
            LakeFishingZonePortal saved = new LakeFishingZonePortal();
            saved.setSpatialPlanningSnapshotId(snapshot.getId());
            saved.setZoneId(row.getId());
            saved.setPortalKey(portal.id());
            saved.setGeom(portal.point());
            saved.setSequence(portalSeq++);
            portalRepository.save(saved);
        }
    }

    private void persistTiles(UUID snapshotId, LakeNavRaster raster) {
        entityManager.flush();
        List<LakeNavTiles.Packed> tiles = raster.packTiles();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO lake_navigation_tiles
                    (id, spatial_planning_snapshot_id, tile_x, tile_y, traversability_mask, clearance_m)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                tiles,
                TILE_JDBC_BATCH,
                (ps, tile) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, snapshotId);
                    ps.setInt(3, tile.tileX());
                    ps.setInt(4, tile.tileY());
                    ps.setBytes(5, tile.mask());
                    ps.setBytes(6, tile.clearance());
                });
        log.info("Spatial snapshot {} persisted {} navigation tiles ({} navigable cells)",
                snapshotId, tiles.size(), raster.navigableCellCount());
    }

    private int precomputeUsefulPairs(
            UUID snapshotId,
            CandidateSpot zone,
            LakeNavRaster raster,
            PlanningProperties.Spatial spatial
    ) {
        List<VisitPortal> portals = zone.getPortals();
        int pairs = 0;
        for (int i = 0; i < portals.size(); i++) {
            for (int j = 0; j < portals.size(); j++) {
                if (i == j) {
                    continue;
                }
                if (savePath(snapshotId, zone.getZoneId(), portals.get(i), portals.get(j), raster, spatial)) {
                    pairs++;
                }
            }
        }
        List<CandidateSpot> members = zone.getZoneMembers();
        if (members.size() <= spatial.getPairwiseFullPortalLimit()) {
            for (int i = 0; i < members.size(); i++) {
                for (int j = 0; j < members.size(); j++) {
                    if (i == j || members.get(i).getEntryPoint() == null || members.get(j).getEntryPoint() == null) {
                        continue;
                    }
                    var path = raster.shortest(
                            members.get(i).getEntryPoint(),
                            members.get(j).getEntryPoint(),
                            spatial.getInternalCruiseKmh());
                    if (path.isEmpty()) {
                        continue;
                    }
                    saveWaterPath(
                            snapshotId,
                            zone.getZoneId(),
                            LakeNavRaster.cellKey(raster.cellOf(members.get(i).getEntryPoint())),
                            LakeNavRaster.cellKey(raster.cellOf(members.get(j).getEntryPoint())),
                            path.get(),
                            true);
                    pairs++;
                }
            }
        }
        return pairs;
    }

    private boolean savePath(
            UUID snapshotId,
            UUID zoneId,
            VisitPortal from,
            VisitPortal to,
            LakeNavRaster raster,
            PlanningProperties.Spatial spatial
    ) {
        var path = raster.shortest(from.point(), to.point(), spatial.getInternalCruiseKmh());
        if (path.isEmpty()) {
            return false;
        }
        saveWaterPath(
                snapshotId,
                zoneId,
                LakeNavRaster.cellKey(raster.cellOf(from.point())),
                LakeNavRaster.cellKey(raster.cellOf(to.point())),
                path.get(),
                true);
        return true;
    }

    private void saveWaterPath(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            boolean precomputed
    ) {
        if (fromKey == null || fromKey.isBlank() || toKey == null || toKey.isBlank()) {
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
        row.setPrecomputed(precomputed);
        row.setNavigationVersion(properties.getSpatial().getNavigationVersion());
        waterPathRepository.save(row);
    }

    private void validate(UUID snapshotId, List<CandidateSpot> atomics, List<CandidateSpot> zones) {
        for (CandidateSpot spot : atomics) {
            if (spot.getTargetKind() == TargetKind.ZONE) {
                throw new IllegalStateException("Atomic target must not be ZONE");
            }
            if (spot.getTargetKind() == TargetKind.AREA) {
                throw new IllegalStateException("Executable AREA is not allowed");
            }
            if (spot.getFishingTargetId() == null || spot.getTargetGeometry() == null) {
                throw new IllegalStateException("Incomplete fishing target");
            }
        }
        for (CandidateSpot zone : zones) {
            if (zone.getTargetGeometry() == null || zone.getTargetGeometry().isEmpty()) {
                throw new IllegalStateException("Empty zone geometry");
            }
            for (CandidateSpot member : zone.getZoneMembers()) {
                if (member.getFishingTargetId() == null) {
                    throw new IllegalStateException("Zone member missing fishingTargetId");
                }
            }
        }
        if (targetRepository.findBySpatialPlanningSnapshotId(snapshotId).isEmpty() && !atomics.isEmpty()) {
            throw new IllegalStateException("Snapshot targets were not persisted");
        }
    }

    private void flushPersistence() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
