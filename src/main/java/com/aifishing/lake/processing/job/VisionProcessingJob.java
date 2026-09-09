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
import com.aifishing.lake.processing.render.BathymetricMapRenderer;
import com.aifishing.lake.processing.render.RenderedTile;
import com.aifishing.lake.processing.service.FeatureReplaceService;
import com.aifishing.lake.processing.service.FeatureStatusService;
import com.aifishing.lake.processing.service.StructureSourceFingerprint;
import com.aifishing.lake.processing.storage.DerivedArtifactService;
import com.aifishing.lake.processing.vision.VisionCandidate;
import com.aifishing.lake.processing.vision.VisionCandidateDeduper;
import com.aifishing.lake.processing.vision.VisionFeatureAssembler;
import com.aifishing.lake.processing.vision.VisionMapClient;
import com.aifishing.lake.repo.LakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VisionProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(VisionProcessingJob.class);

    private final LakeRepository lakeRepository;
    private final AnalysisContextFactory contextFactory;
    private final BathymetricMapRenderer mapRenderer;
    private final VisionMapClient visionMapClient;
    private final VisionFeatureAssembler featureAssembler;
    private final FeatureStatusService featureStatusService;
    private final FeatureReplaceService featureReplaceService;
    private final DerivedArtifactService derivedArtifactService;
    private final ProcessingProperties properties;
    private final PipelineJobSupport jobSupport;
    private final StructureSourceFingerprint fingerprint;

    public VisionProcessingJob(
            LakeRepository lakeRepository,
            AnalysisContextFactory contextFactory,
            BathymetricMapRenderer mapRenderer,
            VisionMapClient visionMapClient,
            VisionFeatureAssembler featureAssembler,
            FeatureStatusService featureStatusService,
            FeatureReplaceService featureReplaceService,
            DerivedArtifactService derivedArtifactService,
            ProcessingProperties properties,
            PipelineJobSupport jobSupport,
            StructureSourceFingerprint fingerprint
    ) {
        this.lakeRepository = lakeRepository;
        this.contextFactory = contextFactory;
        this.mapRenderer = mapRenderer;
        this.visionMapClient = visionMapClient;
        this.featureAssembler = featureAssembler;
        this.featureStatusService = featureStatusService;
        this.featureReplaceService = featureReplaceService;
        this.derivedArtifactService = derivedArtifactService;
        this.properties = properties;
        this.jobSupport = jobSupport;
        this.fingerprint = fingerprint;
    }

    public LakeProcessSummaryResponse run(UUID lakeId) {
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        Instant startedAt = Instant.now();
        String analysisVersion = UUID.randomUUID().toString();
        Map<String, Object> parameters = new LinkedHashMap<>(properties.toSnapshot());
        LakeAnalysisRun analysisRun = jobSupport.openRun(lake, Pipeline.VISION, analysisVersion, parameters, startedAt);
        EnumMap<FeatureType, PipelineJobSupport.TypeOutcome> outcomes = new EnumMap<>(FeatureType.class);
        List<String> errorMessages = new ArrayList<>();
        try {
            AnalysisContext context = contextFactory.create(lake, analysisVersion, analysisRun.getId(), Pipeline.VISION);
            analysisRun.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
            analysisRun.setSourceSnapshotId(fingerprint.id(context.sourceDatasetSnapshot()));
            List<RenderedTile> tiles = mapRenderer.render(context);
            parameters.put("visionTileCount", tiles.size());
            int visionRequestCount = 0;
            derivedArtifactService.persistMap(context, tiles, parameters);
            List<VisionCandidate> candidates = new ArrayList<>();
            boolean anyTileSucceeded = false;
            for (RenderedTile tile : tiles) {
                try {
                    candidates.addAll(visionMapClient.extract(tile, context));
                    visionRequestCount++;
                    anyTileSucceeded = true;
                } catch (Exception ex) {
                    visionRequestCount++;
                    log.warn("Vision tile failed for lake {} r{}c{}: {}", lakeId, tile.row(), tile.col(), ex.getMessage());
                    errorMessages.add("tile r" + tile.row() + "c" + tile.col() + ": " + jobSupport.truncate(ex.getMessage()));
                }
            }
            parameters.put("visionRequestCount", visionRequestCount);
            analysisRun.setParameters(parameters);
            if (!anyTileSucceeded) {
                throw new IllegalStateException(errorMessages.isEmpty()
                        ? "Canonical-render Vision produced no tiles"
                        : String.join("; ", errorMessages));
            }
            List<VisionCandidate> deduped = VisionCandidateDeduper.dedupe(candidates);
            for (FeatureType type : FeatureType.values()) {
                featureStatusService.markAttemptStarted(lakeId, type, Pipeline.VISION);
                try {
                    List<LakeFeature> features = new ArrayList<>();
                    for (VisionCandidate candidate : VisionCandidateDeduper.ofType(deduped, type)) {
                        LakeFeature feature = featureAssembler.toFeature(context, candidate);
                        if (feature != null) {
                            features.add(feature);
                        }
                    }
                    featureReplaceService.replace(lakeId, type, Pipeline.VISION, features);
                    featureStatusService.markAvailable(lakeId, type, Pipeline.VISION, features.size(), Map.of("runnable", true));
                    outcomes.put(type, PipelineJobSupport.TypeOutcome.SUCCESS);
                } catch (Exception ex) {
                    log.warn("Vision persist failed for lake {} type {}: {}", lakeId, type, ex.getMessage());
                    featureStatusService.markFailed(lakeId, type, Pipeline.VISION, jobSupport.truncate(ex.getMessage()));
                    outcomes.put(type, PipelineJobSupport.TypeOutcome.FAILED);
                    errorMessages.add(type + ": " + jobSupport.truncate(ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            log.error("Canonical-render Vision failed for {}", lakeId, ex);
            for (FeatureType type : FeatureType.values()) {
                outcomes.putIfAbsent(type, PipelineJobSupport.TypeOutcome.FAILED);
                featureStatusService.markFailed(lakeId, type, Pipeline.VISION, jobSupport.truncate(ex.getMessage()));
            }
            errorMessages.add(jobSupport.truncate(ex.getMessage()));
        }
        return jobSupport.complete(
                lakeRepository.findById(lakeId).orElse(lake),
                Pipeline.VISION,
                analysisVersion,
                analysisRun,
                startedAt,
                Instant.now(),
                outcomes,
                errorMessages
        );
    }
}
