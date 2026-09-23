package com.aifishing.lake.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Production launcher. {@link InlineLakeOpsJobLauncher}'s 1-thread pool does <strong>not</strong>
 * apply here: each {@code RunTask} is a separate Fargate worker. Cap is
 * {@code app.ops.jobs.ecs.max-concurrent}.
 */
@Component
@ConditionalOnProperty(name = "app.ops.jobs.launcher", havingValue = "ecs")
public class EcsLakeOpsJobLauncher implements LakeOpsJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(EcsLakeOpsJobLauncher.class);
    private static final List<LakeOpsJobStatus> ACTIVE = List.of(LakeOpsJobStatus.QUEUED, LakeOpsJobStatus.RUNNING);

    private final EcsOpsClient ecsOpsClient;
    private final LakeOpsJobRepository repository;
    private final LakeOpsProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Object launchLock = new Object();

    public EcsLakeOpsJobLauncher(
            EcsOpsClient ecsOpsClient,
            LakeOpsJobRepository repository,
            LakeOpsProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.ecsOpsClient = ecsOpsClient;
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterQueued(LakeOpsJob job) {
        tryLaunch(job.getId());
    }

    @Override
    public void launchWaitingJobs() {
        for (LakeOpsJob waiting : repository.findByStatusAndEcsTaskArnIsNullAndLaunchAttemptedAtIsNullOrderByCreatedAtAsc(
                LakeOpsJobStatus.QUEUED
        )) {
            if (!tryLaunch(waiting.getId())) {
                return;
            }
        }
    }

    boolean tryLaunch(UUID jobId) {
        boolean claimed;
        synchronized (launchLock) {
            claimed = claimLaunchSlot(jobId);
        }
        if (!claimed) {
            log.info("lake ops job {} waiting for ECS slot (maxConcurrent={})", jobId, maxConcurrent());
            return false;
        }
        String taskArn = ecsOpsClient.runTask(jobId);
        transactionTemplate.executeWithoutResult(status -> {
            LakeOpsJob stored = repository.findById(jobId).orElseThrow();
            stored.setEcsTaskArn(taskArn);
            stored.setHeartbeatAt(Instant.now());
            repository.save(stored);
        });
        return true;
    }

    private boolean claimLaunchSlot(UUID jobId) {
        LakeOpsJob job = repository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != LakeOpsJobStatus.QUEUED) {
            return false;
        }
        if (job.getEcsTaskArn() != null || job.getLaunchAttemptedAt() != null) {
            return false;
        }
        if (repository.countOccupiedEcsSlots(ACTIVE) >= maxConcurrent()) {
            return false;
        }
        Instant now = Instant.now();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            LakeOpsJob stored = repository.findById(jobId).orElseThrow();
            if (stored.getStatus() != LakeOpsJobStatus.QUEUED
                    || stored.getEcsTaskArn() != null
                    || stored.getLaunchAttemptedAt() != null) {
                return false;
            }
            stored.setLaunchAttemptedAt(now);
            stored.setHeartbeatAt(now);
            repository.save(stored);
            return true;
        }));
    }

    private int maxConcurrent() {
        return Math.max(1, properties.getEcs().getMaxConcurrent());
    }
}
