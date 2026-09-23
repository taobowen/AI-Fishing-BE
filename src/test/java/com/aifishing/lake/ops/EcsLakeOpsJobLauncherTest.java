package com.aifishing.lake.ops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EcsLakeOpsJobLauncherTest {

    @Mock
    EcsOpsClient ecsOpsClient;
    @Mock
    LakeOpsJobRepository repository;
    @Mock
    LakeOpsProperties properties;
    @Mock
    LakeOpsProperties.Ecs ecs;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    TransactionStatus transactionStatus;

    EcsLakeOpsJobLauncher launcher;
    Map<UUID, LakeOpsJob> jobs;
    AtomicInteger runTaskCalls;

    @BeforeEach
    void setUp() {
        jobs = new LinkedHashMap<>();
        runTaskCalls = new AtomicInteger();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(properties.getEcs()).thenReturn(ecs);
        when(ecs.getMaxConcurrent()).thenReturn(2);
        when(ecsOpsClient.runTask(any())).thenAnswer(invocation -> {
            runTaskCalls.incrementAndGet();
            return "arn:aws:ecs:ca-central-1:1:task/cluster/" + invocation.getArgument(0);
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(jobs.get(invocation.getArgument(0))));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.countOccupiedEcsSlots(any())).thenAnswer(invocation -> jobs.values().stream()
                .filter(job -> job.getStatus() == LakeOpsJobStatus.QUEUED || job.getStatus() == LakeOpsJobStatus.RUNNING)
                .filter(job -> job.getEcsTaskArn() != null || job.getLaunchAttemptedAt() != null)
                .count());
        when(repository.findByStatusAndEcsTaskArnIsNullAndLaunchAttemptedAtIsNullOrderByCreatedAtAsc(LakeOpsJobStatus.QUEUED))
                .thenAnswer(invocation -> jobs.values().stream()
                        .filter(job -> job.getStatus() == LakeOpsJobStatus.QUEUED)
                        .filter(job -> job.getEcsTaskArn() == null && job.getLaunchAttemptedAt() == null)
                        .sorted(Comparator.comparing(LakeOpsJob::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList());
        launcher = new EcsLakeOpsJobLauncher(ecsOpsClient, repository, properties, transactionManager);
    }

    @Test
    void maxConcurrentTwoEnqueuesSixteenLaunchExactlyTwo() {
        List<LakeOpsJob> enqueued = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            LakeOpsJob job = queued();
            enqueued.add(job);
            launcher.afterQueued(job);
        }

        verify(ecsOpsClient, times(2)).runTask(any());
        assertThat(runTaskCalls.get()).isEqualTo(2);
        long launched = enqueued.stream().filter(job -> job.getEcsTaskArn() != null).count();
        long waiting = enqueued.stream()
                .filter(job -> job.getStatus() == LakeOpsJobStatus.QUEUED)
                .filter(job -> job.getEcsTaskArn() == null && job.getLaunchAttemptedAt() == null)
                .count();
        assertThat(launched).isEqualTo(2);
        assertThat(waiting).isEqualTo(14);
    }

    @Test
    void completingOneLaunchesNextWaitingJob() {
        List<LakeOpsJob> enqueued = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            LakeOpsJob job = queued();
            enqueued.add(job);
            launcher.afterQueued(job);
        }
        LakeOpsJob first = enqueued.stream().filter(job -> job.getEcsTaskArn() != null).findFirst().orElseThrow();
        first.setStatus(LakeOpsJobStatus.SUCCEEDED);
        first.setFinishedAt(java.time.Instant.now());

        launcher.launchWaitingJobs();

        verify(ecsOpsClient, times(3)).runTask(any());
        assertThat(enqueued.stream().filter(job -> job.getEcsTaskArn() != null).count()).isEqualTo(3);
        assertThat(enqueued.stream()
                .filter(job -> job.getEcsTaskArn() == null && job.getLaunchAttemptedAt() == null)
                .count()).isEqualTo(13);
    }

    private LakeOpsJob queued() {
        LakeOpsJob job = new LakeOpsJob();
        UUID id = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", id);
        job.setLakeId(UUID.randomUUID());
        job.setKind(LakeOpsJobKind.IMPORT);
        job.setStatus(LakeOpsJobStatus.QUEUED);
        job.setCreatedAt(java.time.Instant.now());
        jobs.put(id, job);
        return job;
    }
}
