package com.aifishing.lake.processing.job;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.processing.admin.FeatureTypeStatusResponse;
import com.aifishing.lake.processing.admin.LakeAnalysisQuality;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.LakeProcessingStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.hybrid.HybridParentPins;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PipelineJobSupport {

    public enum TypeOutcome {
        SUCCESS,
        NOT_AVAILABLE,
        FAILED
    }

    private final LakeAnalysisRunRepository analysisRunRepository;
    private final LakeFeatureRepository featureRepository;
    private final ProcessingProperties properties;
    private final AnalysisQualityAssembler qualityAssembler;
    private final com.aifishing.planning.spatial.SpatialSnapshotTrigger spatialSnapshotTrigger;

    public PipelineJobSupport(
            LakeAnalysisRunRepository analysisRunRepository,
            LakeFeatureRepository featureRepository,
            ProcessingProperties properties,
            AnalysisQualityAssembler qualityAssembler,
            com.aifishing.planning.spatial.SpatialSnapshotTrigger spatialSnapshotTrigger
    ) {
        this.analysisRunRepository = analysisRunRepository;
        this.featureRepository = featureRepository;
        this.properties = properties;
        this.qualityAssembler = qualityAssembler;
        this.spatialSnapshotTrigger = spatialSnapshotTrigger;
    }

    public LakeAnalysisRun openRun(Lake lake, Pipeline pipeline, String analysisVersion, Map<String, Object> parameters, Instant startedAt) {
        return openRun(lake, pipeline, analysisVersion, parameters, startedAt, null);
    }

    public LakeAnalysisRun openRun(
            Lake lake,
            Pipeline pipeline,
            String analysisVersion,
            Map<String, Object> parameters,
            Instant startedAt,
            HybridParentPins pins
    ) {
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(lake.getId());
        run.setAnalysisVersion(analysisVersion);
        run.setAlgorithmVersion(properties.getAlgorithmVersion());
        run.setStartedAt(startedAt);
        run.setPipeline(pipeline);
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setParameters(parameters);
        if (pins != null) {
            run.setGisParentRunId(pins.gisParentRunId());
            run.setGisAnalysisVersion(pins.gisAnalysisVersion());
            run.setVisionParentRunId(pins.visionParentRunId());
            run.setVisionAnalysisVersion(pins.visionAnalysisVersion());
            run.setSourceSnapshotId(pins.sourceSnapshotId());
        }
        return analysisRunRepository.save(run);
    }

    public LakeProcessingStatus lakeStatus(EnumMap<FeatureType, TypeOutcome> outcomes) {
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

    public LakeProcessSummaryResponse complete(
            Lake lake,
            Pipeline pipeline,
            String analysisVersion,
            LakeAnalysisRun analysisRun,
            Instant startedAt,
            Instant completedAt,
            EnumMap<FeatureType, TypeOutcome> outcomes,
            List<String> errorMessages
    ) {
        LakeProcessingStatus pipelineStatus = lakeStatus(outcomes);
        AnalysisRunStatus runStatus = AnalysisRunStatus.valueOf(pipelineStatus.name());
        Map<String, Object> featureCounts = new LinkedHashMap<>();
        for (FeatureType type : FeatureType.values()) {
            featureCounts.put(type.name(), featureRepository.countByLakeIdAndPipelineAndType(lake.getId(), pipeline, type));
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

        spatialSnapshotTrigger.afterAnalysis(lake.getId(), pipeline, analysisVersion, pipelineStatus);

        LakeAnalysisQuality quality = qualityAssembler.quality(lake, pipeline);
        List<FeatureTypeStatusResponse> featureStatuses = qualityAssembler.featureStatuses(lake.getId(), pipeline);
        return new LakeProcessSummaryResponse(
                lake.getId(),
                lake.getName(),
                pipeline,
                lake.getProcessingStatus(),
                analysisVersion,
                properties.getAlgorithmVersion(),
                runStatus.name(),
                Duration.between(startedAt, completedAt).toMillis(),
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

    public String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
