package com.aifishing.lake.ops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LakeOpsReconcilerTest {

    @Mock
    LakeOpsJobRepository repository;
    @Mock
    LakeOpsProperties properties;
    @Mock
    EcsOpsClient ecsOpsClient;
    @Mock
    LakeOpsJobLauncher launcher;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    TransactionStatus transactionStatus;

    LakeOpsReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(properties.getLaunchStale()).thenReturn(Duration.ofMinutes(5));
        when(properties.getRunningStale()).thenReturn(Duration.ofMinutes(15));
        reconciler = new LakeOpsReconciler(repository, properties, ecsOpsClient, launcher, transactionManager);
    }

    @Test
    void stoppedEcsTaskMarksJobFailedWithStopReason() {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = runningWithArn(jobId);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(ecsOpsClient.describe(job.getEcsTaskArn())).thenReturn(Optional.of(new EcsOpsClient.EcsTaskSnapshot(
                "STOPPED",
                "STOPPED",
                "EssentialContainerExited",
                "OutOfMemoryError",
                137,
                "OOMKilled"
        )));

        reconciler.reconcile(job);

        ArgumentCaptor<LakeOpsJob> captor = ArgumentCaptor.forClass(LakeOpsJob.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(captor.getValue().getFailureCode()).isEqualTo(LakeOpsFailureCode.WORKER_START_FAILED);
        assertThat(captor.getValue().getErrorMessage())
                .contains("STOPPED")
                .contains("EssentialContainerExited")
                .contains("OutOfMemoryError")
                .contains("137")
                .contains("OOMKilled");
        verify(launcher).launchWaitingJobs();
    }

    @Test
    void queuedWithoutArnOlderThanLaunchStaleFailsAfterLaunchAttempt() {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", jobId);
        job.setStatus(LakeOpsJobStatus.QUEUED);
        job.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        job.setLaunchAttemptedAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        reconciler.reconcile(job);

        verify(repository).save(any(LakeOpsJob.class));
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.WORKER_START_FAILED);
        assertThat(job.getErrorMessage()).contains("task ARN");
    }

    @Test
    void queuedWaitingForSlotIsNotFailedByCreatedAt() {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", jobId);
        job.setStatus(LakeOpsJobStatus.QUEUED);
        job.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        reconciler.reconcile(job);

        verify(repository, never()).save(any(LakeOpsJob.class));
        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.QUEUED);
        verify(launcher, never()).launchWaitingJobs();
    }

    @Test
    void describeEmptyMarksJobFailed() {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = runningWithArn(jobId);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(ecsOpsClient.describe(job.getEcsTaskArn())).thenReturn(Optional.empty());

        reconciler.reconcile(job);

        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.WORKER_START_FAILED);
        verify(launcher).launchWaitingJobs();
    }

    @Test
    void runningWithStaleHeartbeatTimesOut() {
        UUID jobId = UUID.randomUUID();
        LakeOpsJob job = runningWithArn(jobId);
        job.setHeartbeatAt(Instant.now().minus(Duration.ofMinutes(20)));
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(ecsOpsClient.describe(job.getEcsTaskArn())).thenReturn(Optional.of(new EcsOpsClient.EcsTaskSnapshot(
                "RUNNING", "RUNNING", null, null, null, null
        )));

        reconciler.reconcile(job);

        assertThat(job.getStatus()).isEqualTo(LakeOpsJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(LakeOpsFailureCode.JOB_TIMEOUT);
        verify(launcher).launchWaitingJobs();
    }

    private static LakeOpsJob runningWithArn(UUID jobId) {
        LakeOpsJob job = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", jobId);
        job.setStatus(LakeOpsJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setHeartbeatAt(Instant.now());
        job.setEcsTaskArn("arn:aws:ecs:ca-central-1:1:task/cluster/abc");
        return job;
    }
}
