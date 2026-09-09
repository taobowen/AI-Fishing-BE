package com.aifishing.lake.processing.benchmark;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.processing.admin.LakeAnalysisQuality;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.domain.DerivedAnalysisArtifact;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.hybrid.HybridParentResolver;
import com.aifishing.lake.processing.job.HybridProcessingJob;
import com.aifishing.lake.processing.job.ProcessingJobRunner;
import com.aifishing.lake.processing.job.VisionProcessingJob;
import com.aifishing.lake.processing.repo.DerivedAnalysisArtifactRepository;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.storage.DerivedArtifactService;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BenchmarkJob {

    private final LakeRepository lakeRepository;
    private final LakeFeatureRepository featureRepository;
    private final LakeAnalysisRunRepository analysisRunRepository;
    private final DerivedAnalysisArtifactRepository artifactRepository;
    private final ProcessingJobRunner gisJob;
    private final VisionProcessingJob visionJob;
    private final HybridProcessingJob hybridJob;
    private final HybridParentResolver hybridParentResolver;
    private final AnalysisContextFactory contextFactory;
    private final DerivedArtifactService derivedArtifactService;
    private final BenchmarkScorer scorer;
    private final HeadGoldLoader goldLoader;
    private final ExternalScreenshotBaseline screenshotBaseline;
    private final AnalysisQualityAssembler qualityAssembler;

    public BenchmarkJob(
            LakeRepository lakeRepository,
            LakeFeatureRepository featureRepository,
            LakeAnalysisRunRepository analysisRunRepository,
            DerivedAnalysisArtifactRepository artifactRepository,
            ProcessingJobRunner gisJob,
            VisionProcessingJob visionJob,
            HybridProcessingJob hybridJob,
            HybridParentResolver hybridParentResolver,
            AnalysisContextFactory contextFactory,
            DerivedArtifactService derivedArtifactService,
            BenchmarkScorer scorer,
            HeadGoldLoader goldLoader,
            ExternalScreenshotBaseline screenshotBaseline,
            AnalysisQualityAssembler qualityAssembler
    ) {
        this.lakeRepository = lakeRepository;
        this.featureRepository = featureRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.artifactRepository = artifactRepository;
        this.gisJob = gisJob;
        this.visionJob = visionJob;
        this.hybridJob = hybridJob;
        this.hybridParentResolver = hybridParentResolver;
        this.contextFactory = contextFactory;
        this.derivedArtifactService = derivedArtifactService;
        this.scorer = scorer;
        this.goldLoader = goldLoader;
        this.screenshotBaseline = screenshotBaseline;
        this.qualityAssembler = qualityAssembler;
    }

    public Map<String, Object> run(UUID lakeId) {
        LakeProcessSummaryResponse gis = gisJob.run(lakeId);
        LakeProcessSummaryResponse vision = visionJob.run(lakeId);
        LakeProcessSummaryResponse hybrid = hybridJob.run(lakeId, hybridParentResolver.pin(lakeId));
        Lake lake = requireLake(lakeId);
        Map<String, Object> report = compare(lake, gis, vision, hybrid);
        persist(lake, report);
        return report;
    }

    public Map<String, Object> last(UUID lakeId) {
        requireLake(lakeId);
        return artifactRepository.findFirstByLakeIdAndArtifactTypeOrderByCreatedAtDesc(
                        lakeId, DerivedArtifactService.ARTIFACT_BENCHMARK_REPORT)
                .map(DerivedAnalysisArtifact::getGridMetadata)
                .orElse(null);
    }

    public Map<String, Object> compare(Lake lake) {
        return compare(
                lake,
                pipelineSummary(lake, Pipeline.GIS),
                pipelineSummary(lake, Pipeline.VISION),
                pipelineSummary(lake, Pipeline.HYBRID)
        );
    }

    public Map<String, Object> summary(List<UUID> ids) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> lakes = new ArrayList<>();
        for (UUID id : ids) {
            Lake lake = requireLake(id);
            lakes.add(compare(lake));
        }
        root.put("lakes", lakes);
        ExternalScreenshotSection screenshot = screenshotBaseline.load();
        if (screenshot != null) {
            root.put("externalDirectScreenshotVision", screenshot);
        }
        root.put("generatedAt", Instant.now().toString());
        return root;
    }

    private Map<String, Object> compare(
            Lake lake,
            LakeProcessSummaryResponse gis,
            LakeProcessSummaryResponse vision,
            LakeProcessSummaryResponse hybrid
    ) {
        List<LakeFeature> gisFeatures = featureRepository.findByLakeIdAndPipeline(lake.getId(), Pipeline.GIS);
        List<LakeFeature> visionFeatures = featureRepository.findByLakeIdAndPipeline(lake.getId(), Pipeline.VISION);
        List<LakeFeature> hybridFeatures = featureRepository.findByLakeIdAndPipeline(lake.getId(), Pipeline.HYBRID);
        HeadGoldLoader.GoldSet gold = goldLoader.load();
        boolean scoreGold = goldLoader.appliesTo(lake, gold);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("lakeId", lake.getId().toString());
        report.put("lakeName", lake.getName());
        report.put("generatedAt", Instant.now().toString());
        report.put("pipelines", Map.of(
                "GIS", pipelineBlock(lake, Pipeline.GIS, gis, gisFeatures, scoreGold ? gold.features() : null),
                "VISION", pipelineBlock(lake, Pipeline.VISION, vision, visionFeatures, scoreGold ? gold.features() : null),
                "HYBRID", pipelineBlock(lake, Pipeline.HYBRID, hybrid, hybridFeatures, scoreGold ? gold.features() : null)
        ));
        report.put("gisVsVision", scorer.agreement(gisFeatures, visionFeatures));
        ExternalScreenshotSection screenshot = screenshotBaseline.load();
        if (screenshot != null) {
            report.put("externalDirectScreenshotVision", screenshot);
        }
        report.put("acceptance", acceptance(report, screenshot, scoreGold));
        report.put("warnings", warnings(lake, gold, scoreGold));
        return report;
    }

    private Map<String, Object> pipelineBlock(
            Lake lake,
            Pipeline pipeline,
            LakeProcessSummaryResponse summary,
            List<LakeFeature> features,
            List<GoldFeature> gold
    ) {
        AnalysisContext context = contextFactory.create(lake, "benchmark", UUID.randomUUID(), pipeline);
        LakeAnalysisQuality quality = qualityAssembler.quality(lake, pipeline);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("pipeline", pipeline.name());
        if (summary != null) {
            block.put("analysisRunStatus", summary.analysisRunStatus());
            block.put("durationMs", summary.durationMs());
            block.put("analysisVersion", summary.analysisVersion());
        } else {
            block.put("analysisRunStatus", quality.analysisRunStatus());
            block.put("durationMs", quality.processingDurationMs());
            block.put("analysisVersion", quality.currentAnalysisVersion());
        }
        block.put("featureCountByType", quality.featureCountByType());
        block.put("inLakePercent", scorer.inLakePercent(features, context.lakeBoundary()));
        block.put("averageConfidence", quality.averageConfidence());
        block.put("warnings", quality.warnings());
        if (gold != null) {
            block.put("gold", scorer.scoreAgainstGold(features, gold));
        } else {
            block.put("gold", null);
        }
        return block;
    }

    private Map<String, Object> acceptance(
            Map<String, Object> report,
            ExternalScreenshotSection screenshot,
            boolean scoredGold
    ) {
        Map<String, Object> acceptance = new LinkedHashMap<>();
        if (!scoredGold) {
            acceptance.put("status", "NOT_SCORED");
            acceptance.put("notes", "Head human overlay is empty or does not apply to this lake; live F1 is not invented.");
            return acceptance;
        }
        Double hybridF1 = f1(report, "HYBRID");
        Double visionF1 = f1(report, "VISION");
        Double screenshotF1 = screenshot == null ? null : asDouble(screenshot.f1());
        double strongerVision = max(visionF1, screenshotF1);
        acceptance.put("hybridF1", hybridF1);
        acceptance.put("canonicalRenderVisionF1", visionF1);
        acceptance.put("directScreenshotVisionF1", screenshotF1);
        acceptance.put("strongerVisionF1", strongerVision == Double.NEGATIVE_INFINITY ? null : strongerVision);
        if (hybridF1 != null && strongerVision > Double.NEGATIVE_INFINITY) {
            acceptance.put("hybridAtLeastAsGoodAsStrongerVision", hybridF1 + 1e-6 >= strongerVision);
        }
        return acceptance;
    }

    @SuppressWarnings("unchecked")
    private Double f1(Map<String, Object> report, String pipeline) {
        Object pipelines = report.get("pipelines");
        if (!(pipelines instanceof Map<?, ?> map)) {
            return null;
        }
        Object block = map.get(pipeline);
        if (!(block instanceof Map<?, ?> pipelineBlock)) {
            return null;
        }
        Object gold = pipelineBlock.get("gold");
        if (!(gold instanceof Map<?, ?> goldMap)) {
            return null;
        }
        return asDouble(goldMap.get("f1"));
    }

    private double max(Double a, Double b) {
        double value = Double.NEGATIVE_INFINITY;
        if (a != null) {
            value = a;
        }
        if (b != null) {
            value = Math.max(value, b);
        }
        return value;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private List<String> warnings(Lake lake, HeadGoldLoader.GoldSet gold, boolean scoreGold) {
        List<String> warnings = new ArrayList<>();
        if (gold.features().isEmpty()) {
            warnings.add("Head Lake human overlay has no labeled features yet; gold P/R/F1 is omitted.");
        } else if (!scoreGold) {
            warnings.add("Gold overlay is labeled for " + gold.lakeName() + "; not scored on " + lake.getName() + ".");
        }
        if (contextFactory.create(lake, "warn", UUID.randomUUID(), Pipeline.GIS).contours().isEmpty()) {
            warnings.add("No bathymetry contours; canonical-render Vision may hallucinate depth structure and Hybrid should reject unsupported types.");
        }
        warnings.add("Direct Screenshot Vision is an external metrics file only; it is not a backend pipeline.");
        return warnings;
    }

    private LakeProcessSummaryResponse pipelineSummary(Lake lake, Pipeline pipeline) {
        LakeAnalysisRun run = analysisRunRepository.findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lake.getId(), pipeline)
                .orElse(null);
        if (run == null) {
            return null;
        }
        LakeAnalysisQuality quality = qualityAssembler.quality(lake, pipeline);
        return new LakeProcessSummaryResponse(
                lake.getId(),
                lake.getName(),
                pipeline,
                lake.getProcessingStatus(),
                run.getAnalysisVersion(),
                run.getAlgorithmVersion(),
                run.getStatus() == null ? null : run.getStatus().name(),
                quality.processingDurationMs() == null ? 0 : quality.processingDurationMs(),
                run.getStartedAt(),
                run.getCompletedAt(),
                quality.contourCount() == null ? 0 : quality.contourCount(),
                quality.bathymetryPointCount() == null ? 0 : quality.bathymetryPointCount(),
                quality.bathymetryAvailability(),
                quality.featureCountByType(),
                quality.averageConfidence(),
                quality.confidenceDistribution(),
                qualityAssembler.featureStatuses(lake.getId(), pipeline),
                quality.notAvailableFeatureTypes(),
                quality.failedFeatureTypes(),
                quality.warnings()
        );
    }

    private void persist(Lake lake, Map<String, Object> report) {
        LakeAnalysisRun hybridRun = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lake.getId(), Pipeline.HYBRID)
                .orElseThrow();
        AnalysisContext context = contextFactory.create(
                lake,
                hybridRun.getAnalysisVersion(),
                hybridRun.getId(),
                Pipeline.HYBRID
        );
        derivedArtifactService.persistJsonArtifact(
                context,
                DerivedArtifactService.ARTIFACT_BENCHMARK_REPORT,
                report,
                "benchmark-report.json"
        );
    }

    private Lake requireLake(UUID lakeId) {
        return lakeRepository.findById(lakeId).orElseThrow(() -> new NotFoundException("Lake not found"));
    }
}
