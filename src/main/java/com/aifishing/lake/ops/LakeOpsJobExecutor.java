package com.aifishing.lake.ops;

import com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.job.ImportJobRunner;
import com.aifishing.lake.processing.dto.LakeProcessingStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.service.LakeStructureExtractionService;
import com.aifishing.planning.spatial.SpatialSnapshotJob;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class LakeOpsJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(LakeOpsJobExecutor.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final LakeOpsJobRepository repository;
    private final LakeOpsProperties properties;
    private final ImportJobRunner importJobRunner;
    private final LakeStructureExtractionService extractionService;
    private final SpatialSnapshotJob spatialSnapshotJob;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public LakeOpsJobExecutor(
            LakeOpsJobRepository repository,
            LakeOpsProperties properties,
            ImportJobRunner importJobRunner,
            LakeStructureExtractionService extractionService,
            SpatialSnapshotJob spatialSnapshotJob,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.properties = properties;
        this.importJobRunner = importJobRunner;
        this.extractionService = extractionService;
        this.spatialSnapshotJob = spatialSnapshotJob;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @return 0 if claimed and finished (success or failed runner), 0 if duplicate claim, 1 if missing job
     */
    public int claimAndRun(UUID jobId) {
        Boolean claimed = transactionTemplate.execute(status -> repository.claim(jobId, Instant.now()) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            log.info("lake ops job {} not claimed (duplicate or missing QUEUED row)", jobId);
            return 0;
        }
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        MDC.put("opsJobId", job.getId().toString());
        MDC.put("lakeId", job.getLakeId().toString());
        MDC.put("kind", job.getKind().name());
        if (job.getEcsTaskArn() != null) {
            MDC.put("ecsTaskArn", job.getEcsTaskArn());
        }
        Instant started = Instant.now();
        ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "lake-ops-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.max(5, properties.getHeartbeat().toSeconds());
        heartbeats.scheduleAtFixedRate(
                () -> transactionTemplate.executeWithoutResult(status -> repository.heartbeat(jobId, Instant.now())),
                period,
                period,
                TimeUnit.SECONDS
        );
        try {
            Map<String, Object> result = run(job);
            putWorkerStats(result, started);
            complete(jobId, LakeOpsJobStatus.SUCCEEDED, result, null, null);
            log.info(
                    "lake ops job finished status=SUCCEEDED durationMs={} heapUsedBytes={}",
                    result.get("durationMs"),
                    result.get("heapUsedBytes")
            );
            return 0;
        } catch (RuntimeException ex) {
            Map<String, Object> stats = new LinkedHashMap<>();
            LakeOpsFailureCode code = null;
            if (ex instanceof LakeOpsJobException jobEx) {
                code = jobEx.getFailureCode();
                stats.putAll(jobEx.getResult());
            }
            putWorkerStats(stats, started);
            complete(jobId, LakeOpsJobStatus.FAILED, stats, truncate(ex.getMessage()), code);
            log.warn("lake ops job finished status=FAILED failureCode={} durationMs={}", code, stats.get("durationMs"), ex);
            return 1;
        } finally {
            heartbeats.shutdownNow();
            MDC.clear();
        }
    }

    private Map<String, Object> run(LakeOpsJob job) {
        Map<String, Object> params = job.getParams() == null ? Map.of() : job.getParams();
        return switch (job.getKind()) {
            case IMPORT -> importJob(job.getLakeId(), params);
            case PROCESS -> processAndAwaitSnapshot(job.getLakeId(), params);
            case SNAPSHOT -> snapshot(job.getLakeId(), params);
        };
    }

    private Map<String, Object> importJob(UUID lakeId, Map<String, Object> params) {
        DatasetType dataset = enumValue(params.get("dataset"), DatasetType.class);
        LakeImportSummaryResponse summary = dataset == null
                ? importJobRunner.run(lakeId)
                : importJobRunner.run(lakeId, dataset);
        Map<String, Object> result = toMap(summary);
        if (!summary.identityResolved() || summary.ogfId() == null) {
            throw new LakeOpsJobException(
                    LakeOpsFailureCode.IDENTITY_RESOLUTION_FAILED,
                    summary.identityError() == null || summary.identityError().isBlank()
                            ? "IMPORT requires identityResolved and ogfId"
                            : summary.identityError(),
                    result
            );
        }
        return result;
    }

    private Map<String, Object> processAndAwaitSnapshot(UUID lakeId, Map<String, Object> params) {
        Pipeline pipeline = enumValue(params.get("pipeline"), Pipeline.class);
        if (pipeline == null) {
            pipeline = Pipeline.GIS;
        }
        var summary = extractionService.process(lakeId, pipeline);
        Map<String, Object> result = toMap(summary);
        boolean readyOrPartial = LakeProcessingStatus.READY.name().equals(summary.processingStatus())
                || LakeProcessingStatus.PARTIAL.name().equals(summary.processingStatus());
        if (!readyOrPartial) {
            throw new LakeOpsJobException(
                    LakeOpsFailureCode.GIS_PROCESSING_FAILED,
                    "GIS processing status " + summary.processingStatus(),
                    result
            );
        }
        if (pipeline == Pipeline.VISION) {
            return result;
        }
        SpatialPlanningSnapshot snapshot;
        try {
            snapshot = spatialSnapshotJob.buildIfReady(lakeId, pipeline, summary.analysisVersion());
        } catch (RuntimeException ex) {
            throw new LakeOpsJobException(
                    LakeOpsFailureCode.SPATIAL_SNAPSHOT_FAILED,
                    ex.getMessage() == null ? "spatial snapshot failed" : ex.getMessage(),
                    result
            );
        }
        if (snapshot == null) {
            throw new LakeOpsJobException(
                    LakeOpsFailureCode.NO_PERSISTED_FEATURES,
                    "spatial snapshot skipped: no persisted features",
                    result
            );
        }
        if (snapshot.getStatus() != SpatialSnapshotStatus.READY) {
            String message = snapshot.getErrorMessage() == null
                    ? "spatial snapshot " + snapshot.getStatus()
                    : String.valueOf(snapshot.getErrorMessage());
            throw new LakeOpsJobException(LakeOpsFailureCode.SPATIAL_SNAPSHOT_FAILED, message, result);
        }
        result.put("spatialSnapshotId", snapshot.getId().toString());
        result.put("spatialSnapshotStatus", snapshot.getStatus().name());
        return result;
    }

    private Map<String, Object> snapshot(UUID lakeId, Map<String, Object> params) {
        Pipeline pipeline = enumValue(params.get("pipeline"), Pipeline.class);
        if (pipeline == null) {
            pipeline = Pipeline.GIS;
        }
        String analysisVersion = params.get("analysisVersion") instanceof String value && !value.isBlank()
                ? value
                : null;
        if (analysisVersion == null) {
            analysisVersion = extractionService.requireCurrentAnalysisVersion(lakeId);
        }
        SpatialPlanningSnapshot snapshot = spatialSnapshotJob.build(lakeId, pipeline, analysisVersion);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", snapshot.getId().toString());
        out.put("status", snapshot.getStatus().name());
        out.put("featurePipeline", snapshot.getFeaturePipeline().name());
        out.put("featureAnalysisVersion", snapshot.getFeatureAnalysisVersion());
        out.put("counts", snapshot.getCounts());
        out.put("errorMessage", snapshot.getErrorMessage());
        if (snapshot.getStatus() != SpatialSnapshotStatus.READY) {
            String message = snapshot.getErrorMessage() == null
                    ? "spatial snapshot " + snapshot.getStatus()
                    : String.valueOf(snapshot.getErrorMessage());
            throw new LakeOpsJobException(LakeOpsFailureCode.SPATIAL_SNAPSHOT_FAILED, message, out);
        }
        return out;
    }

    private void complete(
            UUID jobId,
            LakeOpsJobStatus status,
            Map<String, Object> result,
            String error,
            LakeOpsFailureCode failureCode
    ) {
        transactionTemplate.executeWithoutResult(tx -> {
            LakeOpsJob job = repository.findById(jobId).orElseThrow();
            if (job.getStatus() != LakeOpsJobStatus.RUNNING) {
                return;
            }
            job.setStatus(status);
            job.setResult(result);
            job.setErrorMessage(error);
            job.setFailureCode(failureCode);
            job.setFinishedAt(Instant.now());
            job.setHeartbeatAt(Instant.now());
            repository.save(job);
        });
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, MAP);
    }

    private static void putWorkerStats(Map<String, Object> result, Instant started) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        result.put("durationMs", Duration.between(started, Instant.now()).toMillis());
        result.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        result.put("heapMaxBytes", memory.getHeapMemoryUsage().getMax());
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "job failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private static <E extends Enum<E>> E enumValue(Object raw, Class<E> type) {
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw);
        if (name.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, name);
    }
}
