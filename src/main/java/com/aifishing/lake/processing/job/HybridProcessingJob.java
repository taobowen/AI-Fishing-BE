package com.aifishing.lake.processing.job;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.hybrid.HybridMergeService;
import com.aifishing.lake.processing.hybrid.HybridParentPins;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.FeatureReplaceService;
import com.aifishing.lake.processing.service.FeatureStatusService;
import com.aifishing.lake.repo.LakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class HybridProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(HybridProcessingJob.class);

    private final LakeRepository lakeRepository;
    private final LakeFeatureRepository featureRepository;
    private final AnalysisContextFactory contextFactory;
    private final HybridMergeService mergeService;
    private final FeatureStatusService featureStatusService;
    private final FeatureReplaceService featureReplaceService;
    private final ProcessingProperties properties;
    private final PipelineJobSupport jobSupport;

    public HybridProcessingJob(
            LakeRepository lakeRepository,
            LakeFeatureRepository featureRepository,
            AnalysisContextFactory contextFactory,
            HybridMergeService mergeService,
            FeatureStatusService featureStatusService,
            FeatureReplaceService featureReplaceService,
            ProcessingProperties properties,
            PipelineJobSupport jobSupport
    ) {
        this.lakeRepository = lakeRepository;
        this.featureRepository = featureRepository;
        this.contextFactory = contextFactory;
        this.mergeService = mergeService;
        this.featureStatusService = featureStatusService;
        this.featureReplaceService = featureReplaceService;
        this.properties = properties;
        this.jobSupport = jobSupport;
    }

    public LakeProcessSummaryResponse run(UUID lakeId, HybridParentPins pins) {
        if (pins == null
                || pins.gisParentRunId() == null
                || pins.gisAnalysisVersion() == null
                || pins.visionParentRunId() == null
                || pins.visionAnalysisVersion() == null
                || pins.sourceSnapshotId() == null) {
            throw new IllegalStateException("Hybrid processing requires pinned GIS and VISION parent runs");
        }
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        Instant startedAt = Instant.now();
        String analysisVersion = UUID.randomUUID().toString();
        Map<String, Object> parameters = properties.toSnapshot();
        LakeAnalysisRun analysisRun = jobSupport.openRun(
                lake, Pipeline.HYBRID, analysisVersion, parameters, startedAt, pins);
        EnumMap<FeatureType, PipelineJobSupport.TypeOutcome> outcomes = new EnumMap<>(FeatureType.class);
        List<String> errorMessages = new ArrayList<>();
        try {
            AnalysisContext context = contextFactory.create(lake, analysisVersion, analysisRun.getId(), Pipeline.HYBRID);
            analysisRun.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
            List<LakeFeature> gisFeatures = featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(
                    lakeId, Pipeline.GIS, pins.gisAnalysisVersion());
            List<LakeFeature> visionFeatures = featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(
                    lakeId, Pipeline.VISION, pins.visionAnalysisVersion());
            Map<FeatureType, List<LakeFeature>> gisByType = gisFeatures.stream()
                    .collect(Collectors.groupingBy(LakeFeature::getType, () -> new EnumMap<>(FeatureType.class), Collectors.toList()));
            Map<FeatureType, List<LakeFeature>> visionByType = visionFeatures.stream()
                    .collect(Collectors.groupingBy(LakeFeature::getType, () -> new EnumMap<>(FeatureType.class), Collectors.toList()));
            for (FeatureType type : FeatureType.values()) {
                featureStatusService.markAttemptStarted(lakeId, type, Pipeline.HYBRID);
                try {
                    List<LakeFeature> vision = visionByType.getOrDefault(type, List.of());
                    List<LakeFeature> gis = gisByType.getOrDefault(type, List.of());
                    List<LakeFeature> merged = mergeService.merge(type, vision, gis, context);
                    featureReplaceService.replace(lakeId, type, Pipeline.HYBRID, merged);
                    featureStatusService.markAvailable(lakeId, type, Pipeline.HYBRID, merged.size(), Map.of("runnable", true));
                    outcomes.put(type, PipelineJobSupport.TypeOutcome.SUCCESS);
                } catch (Exception ex) {
                    log.warn("Hybrid merge failed for lake {} type {}: {}", lakeId, type, ex.getMessage());
                    featureStatusService.markFailed(lakeId, type, Pipeline.HYBRID, jobSupport.truncate(ex.getMessage()));
                    outcomes.put(type, PipelineJobSupport.TypeOutcome.FAILED);
                    errorMessages.add(type + ": " + jobSupport.truncate(ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            log.error("Hybrid analysis failed for {}", lakeId, ex);
            for (FeatureType type : FeatureType.values()) {
                outcomes.putIfAbsent(type, PipelineJobSupport.TypeOutcome.FAILED);
                featureStatusService.markFailed(lakeId, type, Pipeline.HYBRID, jobSupport.truncate(ex.getMessage()));
            }
            errorMessages.add(jobSupport.truncate(ex.getMessage()));
        }
        return jobSupport.complete(
                lakeRepository.findById(lakeId).orElse(lake),
                Pipeline.HYBRID,
                analysisVersion,
                analysisRun,
                startedAt,
                Instant.now(),
                outcomes,
                errorMessages
        );
    }
}
