package com.aifishing.lake.processing.job;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.processing.admin.FeatureTypeStatusResponse;
import com.aifishing.lake.processing.admin.LakeAnalysisQuality;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.LakeProcessingStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.extract.FeatureExtractor;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.FeatureReplaceService;
import com.aifishing.lake.processing.service.FeatureStatusService;
import com.aifishing.lake.processing.service.StructureSourceFingerprint;
import com.aifishing.lake.processing.storage.DerivedArtifactService;
import com.aifishing.lake.repo.LakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LakeProcessingJob implements ProcessingJobRunner {

    private static final Logger log = LoggerFactory.getLogger(LakeProcessingJob.class);

    private final LakeRepository lakeRepository;
    private final LakeAnalysisRunRepository analysisRunRepository;
    private final LakeFeatureRepository featureRepository;
    private final AnalysisContextFactory contextFactory;
    private final Map<FeatureType, FeatureExtractor> extractors;
    private final FeatureStatusService featureStatusService;
    private final FeatureReplaceService featureReplaceService;
    private final DerivedArtifactService derivedArtifactService;
    private final ProcessingProperties properties;
    private final AnalysisQualityAssembler qualityAssembler;
    private final StructureSourceFingerprint fingerprint;
    private final com.aifishing.planning.spatial.SpatialSnapshotTrigger spatialSnapshotTrigger;

    public LakeProcessingJob(
            LakeRepository lakeRepository,
            LakeAnalysisRunRepository analysisRunRepository,
            LakeFeatureRepository featureRepository,
            AnalysisContextFactory contextFactory,
            List<FeatureExtractor> extractors,
            FeatureStatusService featureStatusService,
            FeatureReplaceService featureReplaceService,
            DerivedArtifactService derivedArtifactService,
            ProcessingProperties properties,
            AnalysisQualityAssembler qualityAssembler,
            StructureSourceFingerprint fingerprint,
            com.aifishing.planning.spatial.SpatialSnapshotTrigger spatialSnapshotTrigger
    ) {
        this.lakeRepository = lakeRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.featureRepository = featureRepository;
        this.contextFactory = contextFactory;
        this.extractors = extractors.stream()
                .sorted(Comparator.comparing(FeatureExtractor::type))
                .collect(Collectors.toMap(FeatureExtractor::type, Function.identity(), (a, b) -> a, () -> new EnumMap<>(FeatureType.class)));
        this.featureStatusService = featureStatusService;
        this.featureReplaceService = featureReplaceService;
        this.derivedArtifactService = derivedArtifactService;
        this.properties = properties;
        this.qualityAssembler = qualityAssembler;
        this.fingerprint = fingerprint;
        this.spatialSnapshotTrigger = spatialSnapshotTrigger;
    }

    @Override
    public LakeProcessSummaryResponse run(UUID lakeId) {
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        Instant startedAt = Instant.now();
        String analysisVersion = UUID.randomUUID().toString();
        Map<String, Object> parameters = properties.toSnapshot();

        LakeAnalysisRun analysisRun = openRun(lake, analysisVersion, parameters, startedAt);
        lake.setProcessingStatus(LakeProcessingStatus.PROCESSING.name());
        lake.setProcessingError(null);
        lakeRepository.save(lake);

        EnumMap<FeatureType, TypeOutcome> outcomes = new EnumMap<>(FeatureType.class);
        List<String> errorMessages = new ArrayList<>();
        try {
            AnalysisContext context = contextFactory.create(lake, analysisVersion, analysisRun.getId(), Pipeline.GIS);
            analysisRun.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
            analysisRun.setSourceSnapshotId(fingerprint.id(context.sourceDatasetSnapshot()));
            analysisRunRepository.save(analysisRun);
            derivedArtifactService.persistContourMetadata(context, parameters);

            for (FeatureType type : FeatureType.values()) {
                featureStatusService.markAttemptStarted(lakeId, type, Pipeline.GIS);
                try {
                    if (!runnable(type, lakeId, context)) {
                        featureStatusService.markNotAvailable(
                                lakeId,
                                type,
                                Pipeline.GIS,
                                "Required source datasets are not available",
                                Map.of("runnable", false)
                        );
                        outcomes.put(type, TypeOutcome.NOT_AVAILABLE);
                        continue;
                    }
                    FeatureExtractor extractor = extractors.get(type);
                    if (extractor == null) {
                        throw new IllegalStateException("No extractor registered for " + type);
                    }
                    List<LakeFeature> features = extractor.extract(context);
                    featureReplaceService.replace(lakeId, type, Pipeline.GIS, features);
                    featureStatusService.markAvailable(lakeId, type, Pipeline.GIS, features.size(), Map.of("runnable", true));
                    outcomes.put(type, TypeOutcome.SUCCESS);
                } catch (Exception ex) {
                    log.warn("Feature extraction failed for lake {} type {}: {}", lakeId, type, ex.getMessage());
                    featureStatusService.markFailed(lakeId, type, Pipeline.GIS, truncate(ex.getMessage()));
                    outcomes.put(type, TypeOutcome.FAILED);
                    errorMessages.add(type + ": " + truncate(ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            log.error("Lake structure analysis failed for {}", lakeId, ex);
            for (FeatureType type : FeatureType.values()) {
                outcomes.putIfAbsent(type, TypeOutcome.FAILED);
            }
            errorMessages.add(truncate(ex.getMessage()));
        }

        LakeProcessingStatus lakeStatus = lakeStatus(outcomes);
        AnalysisRunStatus runStatus = AnalysisRunStatus.valueOf(lakeStatus.name());
        Instant completedAt = Instant.now();
        Map<String, Object> featureCounts = new LinkedHashMap<>();
        for (FeatureType type : FeatureType.values()) {
            featureCounts.put(type.name(), featureRepository.countByLakeIdAndPipelineAndType(lakeId, Pipeline.GIS, type));
        }
        Map<String, Object> errorSummary = new LinkedHashMap<>();
        errorSummary.put("messages", errorMessages);
        errorSummary.put("outcomes", outcomes.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name(), (a, b) -> a, LinkedHashMap::new)));

        analysisRun.setCompletedAt(completedAt);
        analysisRun.setStatus(runStatus);
        analysisRun.setFeatureCounts(featureCounts);
        analysisRun.setErrorSummary(errorSummary);
        analysisRunRepository.save(analysisRun);

        lake.setProcessingStatus(lakeStatus.name());
        lake.setCurrentAnalysisVersion(analysisVersion);
        lake.setProcessingError(errorMessages.isEmpty() ? null : String.join("; ", errorMessages));
        lakeRepository.save(lake);
        spatialSnapshotTrigger.afterAnalysis(lakeId, Pipeline.GIS, analysisVersion, lakeStatus);

        Lake reloaded = lakeRepository.findById(lakeId).orElse(lake);
        LakeAnalysisQuality quality = qualityAssembler.quality(reloaded, Pipeline.GIS);
        List<FeatureTypeStatusResponse> featureStatuses = qualityAssembler.featureStatuses(lakeId, Pipeline.GIS);
        return new LakeProcessSummaryResponse(
                reloaded.getId(),
                reloaded.getName(),
                Pipeline.GIS,
                reloaded.getProcessingStatus(),
                analysisVersion,
                properties.getAlgorithmVersion(),
                runStatus.name(),
                java.time.Duration.between(startedAt, completedAt).toMillis(),
                startedAt,
                completedAt,
                quality.contourCount() == null ? 0 : quality.contourCount(),
                quality.bathymetryPointCount() == null ? 0 : quality.bathymetryPointCount(),
                quality.bathymetryAvailability(),
                quality.featureCountByType(),
                quality.averageConfidence(),
                quality.confidenceDistribution(),
                featureStatuses,
                quality.notAvailableFeatureTypes(),
                quality.failedFeatureTypes(),
                quality.warnings()
        );
    }

    private boolean runnable(FeatureType type, UUID lakeId, AnalysisContext context) {
        return switch (type) {
            case HUMP, DROP_OFF, FLAT, BASIN -> contextFactory.bathymetryRunnable(lakeId, context);
            case POINT -> contextFactory.shorelineRunnable(lakeId, context);
            case ISLAND_EDGE -> contextFactory.islandRunnable(lakeId, context);
        };
    }

    private LakeProcessingStatus lakeStatus(EnumMap<FeatureType, TypeOutcome> outcomes) {
        long runnableSuccess = outcomes.values().stream().filter(value -> value == TypeOutcome.SUCCESS).count();
        long runnableFailed = outcomes.values().stream().filter(value -> value == TypeOutcome.FAILED).count();
        if (runnableSuccess == 0) {
            return LakeProcessingStatus.FAILED;
        }
        if (runnableFailed > 0) {
            return LakeProcessingStatus.PARTIAL;
        }
        return LakeProcessingStatus.READY;
    }

    private LakeAnalysisRun openRun(Lake lake, String analysisVersion, Map<String, Object> parameters, Instant startedAt) {
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(lake.getId());
        run.setAnalysisVersion(analysisVersion);
        run.setAlgorithmVersion(properties.getAlgorithmVersion());
        run.setStartedAt(startedAt);
        run.setPipeline(Pipeline.GIS);
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setParameters(parameters);
        return analysisRunRepository.save(run);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private enum TypeOutcome {
        SUCCESS,
        NOT_AVAILABLE,
        FAILED
    }
}
