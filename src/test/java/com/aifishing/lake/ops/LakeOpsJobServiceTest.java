package com.aifishing.lake.ops;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.repo.LakeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LakeOpsJobServiceTest {

    @Mock
    LakeOpsJobRepository repository;
    @Mock
    LakeRepository lakeRepository;
    @Mock
    LakeOpsJobLauncher launcher;
    @Mock
    LakeOpsReconciler reconciler;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    TransactionStatus transactionStatus;

    LakeOpsJobService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new LakeOpsJobService(repository, lakeRepository, launcher, reconciler, transactionManager);
    }

    @Test
    void equivalentActiveJobIsReturnedWithoutLaunch() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LakeOpsJob existing = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", jobId);
        existing.setLakeId(lakeId);
        existing.setKind(LakeOpsJobKind.IMPORT);
        existing.setDedupeKey("*");
        existing.setStatus(LakeOpsJobStatus.QUEUED);
        when(lakeRepository.existsById(lakeId)).thenReturn(true);
        when(repository.findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        LakeOpsJobResponse response = service.enqueueImport(lakeId, null);

        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(LakeOpsJobStatus.QUEUED);
    }

    @Test
    void runTaskFailureAfterInsertMarksFailed() {
        UUID lakeId = UUID.randomUUID();
        AtomicReference<LakeOpsJob> stored = new AtomicReference<>();
        when(lakeRepository.existsById(lakeId)).thenReturn(true);
        when(repository.findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            LakeOpsJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            }
            stored.set(job);
            return job;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        doThrow(new IllegalStateException("cluster quota")).when(launcher).afterQueued(any());

        LakeOpsJobResponse response = service.enqueueImport(lakeId, DatasetType.ACCESS_POINT);

        assertThat(response.status()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo(LakeOpsFailureCode.WORKER_START_FAILED);
        assertThat(response.failureMessage()).contains("cluster quota");
        assertThat(response.errorMessage()).contains("cluster quota");
        assertThat(stored.get().getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(stored.get().getFailureCode()).isEqualTo(LakeOpsFailureCode.WORKER_START_FAILED);
    }

    @Test
    void uniqueViolationReturnsExistingJob() {
        UUID lakeId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LakeOpsJob existing = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", jobId);
        existing.setStatus(LakeOpsJobStatus.RUNNING);
        when(lakeRepository.existsById(lakeId)).thenReturn(true);
        when(repository.findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(any(), any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("active dedupe"));

        LakeOpsJobResponse response = service.enqueueImport(lakeId, null);

        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(LakeOpsJobStatus.RUNNING);
    }
}
