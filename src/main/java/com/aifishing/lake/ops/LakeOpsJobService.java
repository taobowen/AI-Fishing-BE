package com.aifishing.lake.ops;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.repo.LakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LakeOpsJobService {

    private static final Logger log = LoggerFactory.getLogger(LakeOpsJobService.class);
    private static final List<LakeOpsJobStatus> ACTIVE = List.of(LakeOpsJobStatus.QUEUED, LakeOpsJobStatus.RUNNING);

    private final LakeOpsJobRepository repository;
    private final LakeRepository lakeRepository;
    private final LakeOpsJobLauncher launcher;
    private final LakeOpsReconciler reconciler;
    private final TransactionTemplate transactionTemplate;

    public LakeOpsJobService(
            LakeOpsJobRepository repository,
            LakeRepository lakeRepository,
            LakeOpsJobLauncher launcher,
            LakeOpsReconciler reconciler,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.lakeRepository = lakeRepository;
        this.launcher = launcher;
        this.reconciler = reconciler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LakeOpsJobResponse enqueueImport(UUID lakeId, DatasetType dataset) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (dataset != null) {
            params.put("dataset", dataset.name());
        }
        return enqueue(lakeId, LakeOpsJobKind.IMPORT, LakeOpsDedupe.importKey(dataset), params);
    }

    public LakeOpsJobResponse enqueueProcess(UUID lakeId, Pipeline pipeline) {
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pipeline", active.name());
        return enqueue(lakeId, LakeOpsJobKind.PROCESS, LakeOpsDedupe.processKey(active), params);
    }

    public LakeOpsJobResponse enqueueSnapshot(UUID lakeId, Pipeline pipeline, String analysisVersion) {
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pipeline", active.name());
        if (analysisVersion != null && !analysisVersion.isBlank()) {
            params.put("analysisVersion", analysisVersion.trim());
        }
        return enqueue(lakeId, LakeOpsJobKind.SNAPSHOT, LakeOpsDedupe.snapshotKey(active, analysisVersion), params);
    }

    public LakeOpsJobResponse get(UUID jobId) {
        LakeOpsJob job = repository.findById(jobId).orElseThrow(() -> new NotFoundException("Lake ops job not found"));
        reconciler.reconcile(job);
        return LakeOpsJobResponse.from(repository.findById(jobId).orElse(job));
    }

    public void persistTaskArn(UUID jobId, String taskArn) {
        transactionTemplate.executeWithoutResult(status -> {
            LakeOpsJob job = repository.findById(jobId).orElseThrow();
            job.setEcsTaskArn(taskArn);
            job.setHeartbeatAt(Instant.now());
            repository.save(job);
        });
    }

    public void markLaunchFailed(UUID jobId, String message) {
        markLaunchFailed(jobId, LakeOpsFailureCode.WORKER_START_FAILED, message);
    }

    public void markLaunchFailed(UUID jobId, LakeOpsFailureCode failureCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            LakeOpsJob job = repository.findById(jobId).orElseThrow();
            if (job.getStatus() != LakeOpsJobStatus.QUEUED && job.getStatus() != LakeOpsJobStatus.RUNNING) {
                return;
            }
            job.setStatus(LakeOpsJobStatus.FAILED);
            job.setFailureCode(failureCode);
            job.setErrorMessage(truncate(message));
            job.setFinishedAt(Instant.now());
            repository.save(job);
        });
    }

    private LakeOpsJobResponse enqueue(UUID lakeId, LakeOpsJobKind kind, String dedupeKey, Map<String, Object> params) {
        if (!lakeRepository.existsById(lakeId)) {
            throw new NotFoundException("Lake not found");
        }
        var existing = repository.findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(lakeId, kind, dedupeKey, ACTIVE);
        if (existing.isPresent()) {
            return LakeOpsJobResponse.from(existing.get());
        }
        LakeOpsJob job;
        try {
            job = transactionTemplate.execute(status -> {
                LakeOpsJob created = new LakeOpsJob();
                created.setLakeId(lakeId);
                created.setKind(kind);
                created.setDedupeKey(dedupeKey);
                created.setParams(params);
                created.setStatus(LakeOpsJobStatus.QUEUED);
                return repository.saveAndFlush(created);
            });
        } catch (DataIntegrityViolationException ex) {
            return LakeOpsJobResponse.from(repository
                    .findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(lakeId, kind, dedupeKey, ACTIVE)
                    .orElseThrow(() -> ex));
        }
        if (job == null) {
            throw new IllegalStateException("Failed to insert lake ops job");
        }
        try {
            launcher.afterQueued(job);
        } catch (RuntimeException ex) {
            log.warn("lake ops launch failed for {}: {}", job.getId(), ex.getMessage());
            markLaunchFailed(job.getId(), "launch failed: " + ex.getMessage());
        }
        return LakeOpsJobResponse.from(repository.findById(job.getId()).orElse(job));
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "launch failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
