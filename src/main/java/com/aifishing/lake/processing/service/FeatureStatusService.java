package com.aifishing.lake.processing.service;

import com.aifishing.lake.processing.domain.LakeFeatureStatus;
import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.repo.LakeFeatureStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class FeatureStatusService {

    private final LakeFeatureStatusRepository repository;

    public FeatureStatusService(LakeFeatureStatusRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markAttemptStarted(UUID lakeId, FeatureType type) {
        return markAttemptStarted(lakeId, type, Pipeline.GIS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markAttemptStarted(UUID lakeId, FeatureType type, Pipeline pipeline) {
        LakeFeatureStatus status = getOrCreate(lakeId, type, pipeline);
        status.setStatus(FeatureStatusCode.RUNNING);
        status.setLastAttemptedAt(Instant.now());
        status.setErrorMessage(null);
        return repository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markAvailable(UUID lakeId, FeatureType type, int recordCount, Map<String, Object> metadata) {
        return markAvailable(lakeId, type, Pipeline.GIS, recordCount, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markAvailable(
            UUID lakeId,
            FeatureType type,
            Pipeline pipeline,
            int recordCount,
            Map<String, Object> metadata
    ) {
        LakeFeatureStatus status = getOrCreate(lakeId, type, pipeline);
        status.setStatus(FeatureStatusCode.AVAILABLE);
        status.setRecordCount(recordCount);
        status.setLastSuccessfulAnalysisAt(Instant.now());
        status.setErrorMessage(null);
        if (metadata != null) {
            status.setMetadata(metadata);
        }
        if (status.getLastAttemptedAt() == null) {
            status.setLastAttemptedAt(Instant.now());
        }
        return repository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markNotAvailable(UUID lakeId, FeatureType type, String message, Map<String, Object> metadata) {
        return markNotAvailable(lakeId, type, Pipeline.GIS, message, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markNotAvailable(
            UUID lakeId,
            FeatureType type,
            Pipeline pipeline,
            String message,
            Map<String, Object> metadata
    ) {
        LakeFeatureStatus status = getOrCreate(lakeId, type, pipeline);
        status.setStatus(FeatureStatusCode.NOT_AVAILABLE);
        status.setErrorMessage(message);
        if (metadata != null) {
            status.setMetadata(metadata);
        }
        if (status.getLastAttemptedAt() == null) {
            status.setLastAttemptedAt(Instant.now());
        }
        return repository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markFailed(UUID lakeId, FeatureType type, String message) {
        return markFailed(lakeId, type, Pipeline.GIS, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeFeatureStatus markFailed(UUID lakeId, FeatureType type, Pipeline pipeline, String message) {
        LakeFeatureStatus status = getOrCreate(lakeId, type, pipeline);
        status.setStatus(FeatureStatusCode.FAILED);
        status.setErrorMessage(message);
        if (status.getLastAttemptedAt() == null) {
            status.setLastAttemptedAt(Instant.now());
        }
        return repository.save(status);
    }

    private LakeFeatureStatus getOrCreate(UUID lakeId, FeatureType type, Pipeline pipeline) {
        return repository.findByLakeIdAndFeatureTypeAndPipeline(lakeId, type, pipeline)
                .orElseGet(() -> {
                    LakeFeatureStatus created = new LakeFeatureStatus();
                    created.setLakeId(lakeId);
                    created.setFeatureType(type);
                    created.setPipeline(pipeline);
                    created.setStatus(FeatureStatusCode.NOT_CHECKED);
                    return created;
                });
    }
}
