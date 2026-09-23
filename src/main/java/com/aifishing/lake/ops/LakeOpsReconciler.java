package com.aifishing.lake.ops;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class LakeOpsReconciler {

    private final LakeOpsJobRepository repository;
    private final LakeOpsProperties properties;
    private final EcsOpsClient ecsOpsClient;
    private final LakeOpsJobLauncher launcher;
    private final TransactionTemplate transactionTemplate;

    public LakeOpsReconciler(
            LakeOpsJobRepository repository,
            LakeOpsProperties properties,
            EcsOpsClient ecsOpsClient,
            LakeOpsJobLauncher launcher,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.properties = properties;
        this.ecsOpsClient = ecsOpsClient;
        this.launcher = launcher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void reconcileActive() {
        for (LakeOpsJob job : repository.findByStatusIn(List.of(LakeOpsJobStatus.QUEUED, LakeOpsJobStatus.RUNNING))) {
            reconcile(job);
        }
        launcher.launchWaitingJobs();
    }

    public void reconcile(LakeOpsJob job) {
        if (job.getStatus() != LakeOpsJobStatus.QUEUED && job.getStatus() != LakeOpsJobStatus.RUNNING) {
            return;
        }
        boolean failed = false;
        if (job.getEcsTaskArn() != null && !job.getEcsTaskArn().isBlank()) {
            var described = ecsOpsClient.describe(job.getEcsTaskArn());
            if (described.isEmpty()) {
                fail(job.getId(), LakeOpsFailureCode.WORKER_START_FAILED, "ECS task no longer exists");
                failed = true;
            } else if (described.get().stopped()) {
                fail(job.getId(), LakeOpsFailureCode.WORKER_START_FAILED, described.get().failureMessage());
                failed = true;
            }
        } else if (job.getStatus() == LakeOpsJobStatus.QUEUED
                && job.getLaunchAttemptedAt() != null
                && job.getLaunchAttemptedAt().isBefore(Instant.now().minus(properties.getLaunchStale()))) {
            fail(job.getId(), LakeOpsFailureCode.WORKER_START_FAILED, "ECS launch did not persist a task ARN");
            failed = true;
        }
        if (!failed
                && job.getStatus() == LakeOpsJobStatus.RUNNING
                && staleHeartbeat(job)) {
            fail(job.getId(), LakeOpsFailureCode.JOB_TIMEOUT, "RUNNING heartbeat older than running-stale");
            failed = true;
        }
        if (failed) {
            launcher.launchWaitingJobs();
        }
    }

    private boolean staleHeartbeat(LakeOpsJob job) {
        Instant heartbeat = job.getHeartbeatAt() != null ? job.getHeartbeatAt() : job.getStartedAt();
        return heartbeat != null && heartbeat.isBefore(Instant.now().minus(properties.getRunningStale()));
    }

    private void fail(UUID jobId, LakeOpsFailureCode failureCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            LakeOpsJob job = repository.findById(jobId).orElseThrow();
            if (job.getStatus() != LakeOpsJobStatus.QUEUED && job.getStatus() != LakeOpsJobStatus.RUNNING) {
                return;
            }
            job.setStatus(LakeOpsJobStatus.FAILED);
            job.setFailureCode(failureCode);
            job.setErrorMessage(message);
            job.setFinishedAt(Instant.now());
            repository.save(job);
        });
    }
}
