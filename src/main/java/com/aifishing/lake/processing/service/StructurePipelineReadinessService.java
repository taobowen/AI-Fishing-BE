package com.aifishing.lake.processing.service;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class StructurePipelineReadinessService {

    private final LakeRepository lakeRepository;
    private final LakeAnalysisRunRepository analysisRunRepository;
    private final LakeFeatureRepository featureRepository;
    private final AnalysisContextFactory contextFactory;
    private final StructureSourceFingerprint fingerprint;

    public StructurePipelineReadinessService(
            LakeRepository lakeRepository,
            LakeAnalysisRunRepository analysisRunRepository,
            LakeFeatureRepository featureRepository,
            AnalysisContextFactory contextFactory,
            StructureSourceFingerprint fingerprint
    ) {
        this.lakeRepository = lakeRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.featureRepository = featureRepository;
        this.contextFactory = contextFactory;
        this.fingerprint = fingerprint;
    }

    @Transactional(readOnly = true)
    public StructurePipelineReadiness evaluate(UUID lakeId, Pipeline pipeline) {
        if (pipeline == null) {
            return StructurePipelineReadiness.of(
                    Pipeline.GIS, StructurePipelineAvailability.NOT_PROCESSED, null, 0, null);
        }
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        LakeAnalysisRun run = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lakeId, pipeline)
                .orElse(null);
        if (run == null || run.getStatus() == null || run.getStatus() == AnalysisRunStatus.RUNNING) {
            return StructurePipelineReadiness.of(pipeline, StructurePipelineAvailability.NOT_PROCESSED, null, 0, run);
        }
        if (run.getStatus() == AnalysisRunStatus.FAILED) {
            return StructurePipelineReadiness.of(pipeline, StructurePipelineAvailability.FAILED, run.getAnalysisVersion(), 0, run);
        }
        if (pipeline == Pipeline.HYBRID) {
            StructurePipelineAvailability provenance = hybridProvenance(run, lake);
            if (provenance != StructurePipelineAvailability.READY) {
                return StructurePipelineReadiness.of(pipeline, provenance, run.getAnalysisVersion(), 0, run);
            }
        } else if (blank(run.getSourceSnapshotId())) {
            return StructurePipelineReadiness.of(
                    pipeline, StructurePipelineAvailability.PROVENANCE_INVALID, run.getAnalysisVersion(), 0, run);
        } else if (!sourceFingerprintCurrent(run, currentFingerprint(lake))) {
            return StructurePipelineReadiness.of(
                    pipeline, StructurePipelineAvailability.STALE, run.getAnalysisVersion(), 0, run);
        }
        if (run.getAnalysisVersion() == null || run.getAnalysisVersion().isBlank()) {
            return StructurePipelineReadiness.of(
                    pipeline, StructurePipelineAvailability.PROVENANCE_INVALID, run.getAnalysisVersion(), 0, run);
        }
        long featureCount = featureRepository.countByLakeIdAndPipelineAndAnalysisVersion(
                lakeId, pipeline, run.getAnalysisVersion());
        if (featureCount <= 0) {
            return StructurePipelineReadiness.of(
                    pipeline, StructurePipelineAvailability.EMPTY, run.getAnalysisVersion(), 0, run);
        }
        if (run.getStatus() != AnalysisRunStatus.READY && run.getStatus() != AnalysisRunStatus.PARTIAL) {
            return StructurePipelineReadiness.of(
                    pipeline, StructurePipelineAvailability.NOT_PROCESSED, run.getAnalysisVersion(), featureCount, run);
        }
        return StructurePipelineReadiness.of(
                pipeline, StructurePipelineAvailability.READY, run.getAnalysisVersion(), featureCount, run);
    }

    public String currentFingerprint(Lake lake) {
        return fingerprint.id(contextFactory.sourceDatasetSnapshot(lake));
    }

    private StructurePipelineAvailability hybridProvenance(LakeAnalysisRun run, Lake lake) {
        if (run.getGisParentRunId() == null
                || blank(run.getGisAnalysisVersion())
                || run.getVisionParentRunId() == null
                || blank(run.getVisionAnalysisVersion())
                || blank(run.getSourceSnapshotId())) {
            return StructurePipelineAvailability.PROVENANCE_INVALID;
        }
        LakeAnalysisRun gis = analysisRunRepository.findById(run.getGisParentRunId()).orElse(null);
        LakeAnalysisRun vision = analysisRunRepository.findById(run.getVisionParentRunId()).orElse(null);
        if (gis == null || vision == null
                || gis.getPipeline() != Pipeline.GIS
                || vision.getPipeline() != Pipeline.VISION
                || !run.getGisAnalysisVersion().equals(gis.getAnalysisVersion())
                || !run.getVisionAnalysisVersion().equals(vision.getAnalysisVersion())
                || blank(gis.getSourceSnapshotId())
                || blank(vision.getSourceSnapshotId())
                || !gis.getSourceSnapshotId().equals(vision.getSourceSnapshotId())
                || !run.getSourceSnapshotId().equals(gis.getSourceSnapshotId())) {
            return StructurePipelineAvailability.PROVENANCE_INVALID;
        }
        if (!sourceFingerprintCurrent(run, currentFingerprint(lake))) {
            return StructurePipelineAvailability.STALE;
        }
        return StructurePipelineAvailability.READY;
    }

    private boolean sourceFingerprintCurrent(LakeAnalysisRun run, String currentFp) {
        if (run == null || currentFp == null) {
            return false;
        }
        if (Objects.equals(run.getSourceSnapshotId(), currentFp)) {
            return true;
        }
        return currentFp.equals(fingerprint.id(contextFactory.structureSourceSubset(run.getSourceDatasetSnapshot())));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
