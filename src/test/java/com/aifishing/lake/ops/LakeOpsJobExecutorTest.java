package com.aifishing.lake.ops;

import com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse;
import com.aifishing.lake.ingestion.job.ImportJobRunner;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.service.LakeStructureExtractionService;
import com.aifishing.planning.spatial.SpatialSnapshotJob;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LakeOpsJobExecutorTest {

    @Mock
    LakeOpsJobRepository repository;
    @Mock
    LakeOpsProperties properties;
    @Mock
    ImportJobRunner importJobRunner;
    @Mock
    LakeStructureExtractionService extractionService;
    @Mock
    SpatialSnapshotJob spatialSnapshotJob;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    TransactionStatus transactionStatus;

    LakeOpsJobExecutor executor;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(properties.getHeartbeat()).thenReturn(Duration.ofSeconds(30));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        executor = new LakeOpsJobExecutor(
                repository,
                properties,
                importJobRunner,
                extractionService,
                spatialSnapshotJob,
                objectMapper,
                transactionManager
        );
    }

    @Test
    void duplicateClaimDoesNotRunImport() {
        UUID jobId = UUID.randomUUID();
        when(repository.claim(any(), any())).thenReturn(0);

        assertThat(executor.claimAndRun(jobId)).isZero();
        verify(importJobRunner, never()).run(any(UUID.class));
        verify(extractionService, never()).process(any(), any());
    }

    @Test
    void processDoesNotSucceedUntilBuildIfReadyReturnsReady() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.PROCESS, Map.of("pipeline", "GIS"));
        when(extractionService.process(lakeId, Pipeline.GIS)).thenReturn(summary(lakeId, "READY"));
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setStatus(SpatialSnapshotStatus.READY);
        when(spatialSnapshotJob.buildIfReady(lakeId, Pipeline.GIS, "v1")).thenReturn(snapshot);

        assertThat(executor.claimAndRun(jobId)).isZero();
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.SUCCEEDED);
        assertThat(job.getResult()).containsEntry("spatialSnapshotStatus", "READY");
        assertThat(job.getFailureCode()).isNull();
        verify(spatialSnapshotJob, never()).submitIfReady(any(), any(), any());
    }

    @Test
    void processFailsWhenBuildIfReadyIsNotReady() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.PROCESS, Map.of("pipeline", "GIS"));
        when(extractionService.process(lakeId, Pipeline.GIS)).thenReturn(summary(lakeId, "READY"));
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setStatus(SpatialSnapshotStatus.FAILED);
        snapshot.setErrorMessage("snapshot boom");
        when(spatialSnapshotJob.buildIfReady(lakeId, Pipeline.GIS, "v1")).thenReturn(snapshot);

        assertThat(executor.claimAndRun(jobId)).isEqualTo(1);
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.SPATIAL_SNAPSHOT_FAILED);
        assertThat(job.getErrorMessage()).contains("snapshot boom");
        verify(spatialSnapshotJob, never()).submitIfReady(any(), any(), any());
    }

    @Test
    void processFailsWhenGisStatusIsFailed() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.PROCESS, Map.of("pipeline", "GIS"));
        when(extractionService.process(lakeId, Pipeline.GIS)).thenReturn(summary(lakeId, "FAILED"));

        assertThat(executor.claimAndRun(jobId)).isEqualTo(1);
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.GIS_PROCESSING_FAILED);
        verify(spatialSnapshotJob, never()).buildIfReady(any(), any(), any());
    }

    @Test
    void processFailsWhenBuildIfReadyIsNull() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.PROCESS, Map.of("pipeline", "GIS"));
        when(extractionService.process(lakeId, Pipeline.GIS)).thenReturn(summary(lakeId, "READY"));
        when(spatialSnapshotJob.buildIfReady(lakeId, Pipeline.GIS, "v1")).thenReturn(null);

        assertThat(executor.claimAndRun(jobId)).isEqualTo(1);
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.NO_PERSISTED_FEATURES);
    }

    @Test
    void visionProcessSucceedsWithoutSnapshot() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.PROCESS, Map.of("pipeline", "VISION"));
        when(extractionService.process(lakeId, Pipeline.VISION)).thenReturn(visionSummary(lakeId, "PARTIAL"));

        assertThat(executor.claimAndRun(jobId)).isZero();
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.SUCCEEDED);
        verify(spatialSnapshotJob, never()).buildIfReady(any(), any(), any());
    }

    @Test
    void importFailsWhenIdentityIsNotResolved() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.IMPORT, Map.of());
        when(importJobRunner.run(lakeId)).thenReturn(new LakeImportSummaryResponse(
                lakeId, "Professor's Lake", false, "no unique OHN", null, null, List.of()
        ));

        assertThat(executor.claimAndRun(jobId)).isEqualTo(1);
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.IDENTITY_RESOLUTION_FAILED);
        assertThat(job.getResult()).containsEntry("identityResolved", false);
    }

    @Test
    void importSucceedsWhenIdentityAndOgfIdPresent() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = queuedJob(lakeId, LakeOpsJobKind.IMPORT, Map.of());
        when(importJobRunner.run(lakeId)).thenReturn(new LakeImportSummaryResponse(
                lakeId, "Heart Lake", true, null, 127163933L, "Heart Lake", List.of()
        ));

        assertThat(executor.claimAndRun(jobId)).isZero();
        LakeOpsJob job = repository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.SUCCEEDED);
        assertThat(job.getFailureCode()).isNull();
        assertThat(job.getResult()).containsEntry("identityResolved", true);
    }

    private UUID queuedJob(UUID lakeId, LakeOpsJobKind kind, Map<String, Object> params) {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", jobId);
        job.setLakeId(lakeId);
        job.setKind(kind);
        job.setParams(params);
        job.setStatus(LakeOpsJobStatus.QUEUED);
        when(repository.claim(any(), any())).thenAnswer(invocation -> {
            job.setStatus(LakeOpsJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            return 1;
        });
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return jobId;
    }

    private static LakeProcessSummaryResponse summary(UUID lakeId, String status) {
        return processSummary(lakeId, Pipeline.GIS, status);
    }

    private static LakeProcessSummaryResponse visionSummary(UUID lakeId, String status) {
        return processSummary(lakeId, Pipeline.VISION, status);
    }

    private static LakeProcessSummaryResponse processSummary(UUID lakeId, Pipeline pipeline, String status) {
        Instant now = Instant.now();
        return new LakeProcessSummaryResponse(
                lakeId,
                "Head Lake",
                pipeline,
                status,
                "v1",
                "1.0.0",
                "COMPLETED",
                10L,
                now,
                now,
                1,
                1,
                Map.of(),
                Map.of(),
                0.5,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
