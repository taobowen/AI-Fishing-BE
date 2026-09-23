package com.aifishing.lake.processing.service;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.processing.admin.GeoJsonGeometryWriter;
import com.aifishing.lake.processing.admin.LakeAnalysisQuality;
import com.aifishing.lake.processing.admin.LakeFeaturesResponse;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.benchmark.BenchmarkJob;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.hybrid.HybridParentPins;
import com.aifishing.lake.processing.hybrid.HybridParentResolver;
import com.aifishing.lake.processing.job.HybridProcessingJob;
import com.aifishing.lake.processing.job.ProcessingJobRunner;
import com.aifishing.lake.processing.job.VisionProcessingJob;
import com.aifishing.lake.processing.render.CanonicalMapService;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LakeStructureExtractionService {

    private final ProcessingJobRunner processingJobRunner;
    private final VisionProcessingJob visionProcessingJob;
    private final HybridProcessingJob hybridProcessingJob;
    private final LakeRepository lakeRepository;
    private final LakeFeatureRepository featureRepository;
    private final AnalysisQualityAssembler qualityAssembler;
    private final CanonicalMapService canonicalMapService;
    private final BenchmarkJob benchmarkJob;
    private final StructurePipelineReadinessService readinessService;
    private final HybridParentResolver hybridParentResolver;
    private final com.aifishing.planning.spatial.SpatialSnapshotJob spatialSnapshotJob;
    private final com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository spatialSnapshotRepository;

    public LakeStructureExtractionService(
            ProcessingJobRunner processingJobRunner,
            VisionProcessingJob visionProcessingJob,
            HybridProcessingJob hybridProcessingJob,
            LakeRepository lakeRepository,
            LakeFeatureRepository featureRepository,
            AnalysisQualityAssembler qualityAssembler,
            CanonicalMapService canonicalMapService,
            BenchmarkJob benchmarkJob,
            StructurePipelineReadinessService readinessService,
            HybridParentResolver hybridParentResolver,
            com.aifishing.planning.spatial.SpatialSnapshotJob spatialSnapshotJob,
            com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository spatialSnapshotRepository
    ) {
        this.processingJobRunner = processingJobRunner;
        this.visionProcessingJob = visionProcessingJob;
        this.hybridProcessingJob = hybridProcessingJob;
        this.lakeRepository = lakeRepository;
        this.featureRepository = featureRepository;
        this.qualityAssembler = qualityAssembler;
        this.canonicalMapService = canonicalMapService;
        this.benchmarkJob = benchmarkJob;
        this.readinessService = readinessService;
        this.hybridParentResolver = hybridParentResolver;
        this.spatialSnapshotJob = spatialSnapshotJob;
        this.spatialSnapshotRepository = spatialSnapshotRepository;
    }

    public LakeProcessSummaryResponse process(UUID lakeId) {
        return process(lakeId, Pipeline.GIS);
    }

    public LakeProcessSummaryResponse process(UUID lakeId, Pipeline pipeline) {
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        return switch (active) {
            case GIS -> processingJobRunner.run(lakeId);
            case VISION -> visionProcessingJob.run(lakeId);
            case HYBRID -> {
                ensureCurrent(lakeId, Pipeline.GIS);
                ensureCurrent(lakeId, Pipeline.VISION);
                HybridParentPins pins = hybridParentResolver.pin(lakeId);
                yield hybridProcessingJob.run(lakeId, pins);
            }
        };
    }

    public Map<String, Object> rebuildSpatialSnapshot(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        Lake lake = requireLake(lakeId);
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        String version = analysisVersion == null || analysisVersion.isBlank()
                ? lake.getCurrentAnalysisVersion()
                : analysisVersion;
        var snapshot = spatialSnapshotJob.submit(lakeId, active, version);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", snapshot.getId().toString());
        out.put("status", snapshot.getStatus().name());
        out.put("featurePipeline", snapshot.getFeaturePipeline().name());
        out.put("featureAnalysisVersion", snapshot.getFeatureAnalysisVersion());
        out.put("counts", snapshot.getCounts());
        out.put("errorMessage", snapshot.getErrorMessage());
        return out;
    }

    public Map<String, Object> spatialSnapshots(UUID lakeId) {
        requireLake(lakeId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var snapshot : spatialSnapshotRepository.findByLakeIdOrderByCreatedAtDesc(lakeId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", snapshot.getId().toString());
            row.put("status", snapshot.getStatus().name());
            row.put("featurePipeline", snapshot.getFeaturePipeline().name());
            row.put("featureAnalysisVersion", snapshot.getFeatureAnalysisVersion());
            row.put("targetDerivationVersion", snapshot.getTargetDerivationVersion());
            row.put("zoneBuilderVersion", snapshot.getZoneBuilderVersion());
            row.put("navigationVersion", snapshot.getNavigationVersion());
            row.put("counts", snapshot.getCounts());
            row.put("errorMessage", snapshot.getErrorMessage());
            rows.add(row);
        }
        return Map.of("snapshots", rows);
    }

    public Map<String, Object> benchmark(UUID lakeId) {
        return benchmarkJob.run(lakeId);
    }

    public Map<String, Object> lastBenchmark(UUID lakeId) {
        Map<String, Object> report = benchmarkJob.last(lakeId);
        if (report == null) {
            throw new NotFoundException("No benchmark report for this lake");
        }
        return report;
    }

    public Map<String, Object> benchmarkSummary(List<UUID> ids) {
        return benchmarkJob.summary(ids);
    }

    public byte[] mapPng(UUID lakeId) {
        requireLake(lakeId);
        return canonicalMapService.png(lakeId);
    }

    public String requireCurrentAnalysisVersion(UUID lakeId) {
        Lake lake = requireLake(lakeId);
        String version = lake.getCurrentAnalysisVersion();
        if (version == null || version.isBlank()) {
            throw new NotFoundException("Lake has no current analysis version");
        }
        return version;
    }

    @Transactional(readOnly = true)
    public LakeFeaturesResponse features(UUID lakeId) {
        return features(lakeId, Pipeline.GIS);
    }

    @Transactional(readOnly = true)
    public LakeFeaturesResponse features(UUID lakeId, Pipeline pipeline) {
        Lake lake = requireLake(lakeId);
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        LakeAnalysisQuality quality = qualityAssembler.quality(lake, active);
        return new LakeFeaturesResponse(
                lake.getId(),
                lake.getName(),
                active,
                lake.getProcessingStatus(),
                lake.getCurrentAnalysisVersion(),
                quality.featureCountByType(),
                quality.averageConfidence(),
                quality.confidenceDistribution(),
                qualityAssembler.featureStatuses(lakeId, active),
                quality.warnings()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> featuresGeoJson(
            UUID lakeId,
            FeatureType type,
            Double minConfidence,
            String analysisVersion
    ) {
        return featuresGeoJson(lakeId, Pipeline.GIS, type, minConfidence, analysisVersion);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> featuresGeoJson(
            UUID lakeId,
            Pipeline pipeline,
            FeatureType type,
            Double minConfidence,
            String analysisVersion
    ) {
        requireLake(lakeId);
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        List<LakeFeature> features = featureRepository.findByLakeIdAndPipeline(lakeId, active).stream()
                .filter(feature -> type == null || feature.getType() == type)
                .filter(feature -> analysisVersion == null || analysisVersion.equals(feature.getAnalysisVersion()))
                .filter(feature -> minConfidence == null
                        || feature.getConfidence().compareTo(BigDecimal.valueOf(minConfidence)) >= 0)
                .toList();
        List<Map<String, Object>> geoFeatures = new ArrayList<>();
        for (LakeFeature feature : features) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("id", feature.getId());
            properties.put("type", feature.getType().name());
            properties.put("pipeline", feature.getPipeline() == null ? active.name() : feature.getPipeline().name());
            properties.put("confidence", feature.getConfidence());
            properties.put("analysisVersion", feature.getAnalysisVersion());
            properties.put("sourceMethod", feature.getSourceMethod());
            properties.put("minDepthM", feature.getMinDepthM());
            properties.put("maxDepthM", feature.getMaxDepthM());
            properties.put("slope", feature.getSlope());
            properties.put("orientation", feature.getOrientation());
            properties.put("areaM2", feature.getAreaM2());
            Map<String, Object> geoFeature = new LinkedHashMap<>();
            geoFeature.put("type", "Feature");
            geoFeature.put("id", feature.getId().toString());
            geoFeature.put("geometry", GeoJsonGeometryWriter.toGeoJson(feature.getGeometry()));
            geoFeature.put("properties", properties);
            geoFeatures.add(geoFeature);
        }
        return GeoJsonGeometryWriter.featureCollection(geoFeatures);
    }

    @Transactional(readOnly = true)
    public LakeAnalysisQuality analysisQuality(UUID lakeId) {
        return qualityAssembler.quality(requireLake(lakeId));
    }

    private void ensureCurrent(UUID lakeId, Pipeline pipeline) {
        StructurePipelineReadiness readiness = readinessService.evaluate(lakeId, pipeline);
        if (readiness.available()) {
            return;
        }
        if (pipeline == Pipeline.GIS) {
            processingJobRunner.run(lakeId);
        } else if (pipeline == Pipeline.VISION) {
            visionProcessingJob.run(lakeId);
        }
    }

    private Lake requireLake(UUID lakeId) {
        return lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
    }
}
