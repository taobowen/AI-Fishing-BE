package com.aifishing.lake.ingestion.service;

import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DatasetStatusService {

    private final LakeDatasetStatusRepository repository;

    public DatasetStatusService(LakeDatasetStatusRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeDatasetStatus markAttemptStarted(UUID lakeId, DatasetType type, String provider, String sourceReference) {
        LakeDatasetStatus status = getOrCreate(lakeId, type, provider);
        status.setStatus(DatasetStatusCode.IMPORTING);
        status.setLastAttemptedAt(Instant.now());
        status.setSourceReference(sourceReference);
        status.setErrorMessage(null);
        return repository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LakeDatasetStatus markOutcome(
            UUID lakeId,
            DatasetType type,
            String provider,
            DatasetStatusCode outcome,
            Integer recordCount,
            String sourceReference,
            Map<String, Object> metadata,
            String errorMessage
    ) {
        LakeDatasetStatus status = getOrCreate(lakeId, type, provider);
        status.setStatus(outcome);
        if (outcome != DatasetStatusCode.FAILED) {
            status.setRecordCount(recordCount);
        }
        if (sourceReference != null) {
            status.setSourceReference(sourceReference);
        }
        if (metadata != null && !metadata.isEmpty()) {
            status.setMetadata(metadata);
        }
        status.setErrorMessage(errorMessage);
        if (outcome == DatasetStatusCode.AVAILABLE || outcome == DatasetStatusCode.PARTIAL) {
            status.setLastSuccessfulImportAt(Instant.now());
        }
        if (status.getLastAttemptedAt() == null) {
            status.setLastAttemptedAt(Instant.now());
        }
        return repository.save(status);
    }

    private LakeDatasetStatus getOrCreate(UUID lakeId, DatasetType type, String provider) {
        return repository.findByLakeIdAndDatasetTypeAndProvider(lakeId, type, provider)
                .orElseGet(() -> {
                    LakeDatasetStatus created = new LakeDatasetStatus();
                    created.setLakeId(lakeId);
                    created.setDatasetType(type);
                    created.setProvider(provider);
                    created.setStatus(DatasetStatusCode.NOT_CHECKED);
                    return created;
                });
    }
}
