package com.aifishing.lake.ops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InlineLakeOpsJobLauncherTest {

    @Mock
    LakeOpsJobExecutor executor;

    InlineLakeOpsJobLauncher launcher;

    @AfterEach
    void tearDown() {
        if (launcher != null) {
            launcher.shutdown();
        }
    }

    @Test
    void afterQueuedReturnsBeforeClaimAndRunFinishes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(executor.claimAndRun(any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
            }
            return 0;
        });
        launcher = new InlineLakeOpsJobLauncher(executor);
        LakeOpsJob job = job();

        long startedAt = System.nanoTime();
        launcher.afterQueued(job);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isLessThan(250);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void inlineClaimAndRunDrainsSerially_notTheEcsConcurrencyProof() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger overlapping = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicLong secondStartWhileFirstRunning = new AtomicLong();
        when(executor.claimAndRun(any())).thenAnswer(invocation -> {
            if (running.incrementAndGet() > 1) {
                overlapping.incrementAndGet();
            }
            UUID jobId = invocation.getArgument(0);
            if (jobId.equals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"))) {
                firstStarted.countDown();
                firstRelease.await(5, TimeUnit.SECONDS);
            } else {
                secondStartWhileFirstRunning.set(running.get());
                secondStarted.countDown();
            }
            running.decrementAndGet();
            return 0;
        });
        launcher = new InlineLakeOpsJobLauncher(executor);

        LakeOpsJob first = job();
        org.springframework.test.util.ReflectionTestUtils.setField(first, "id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"));
        LakeOpsJob second = job();
        org.springframework.test.util.ReflectionTestUtils.setField(second, "id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002"));

        launcher.afterQueued(first);
        launcher.afterQueued(second);
        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondStarted.getCount()).isEqualTo(1);
        firstRelease.countDown();
        assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(overlapping.get()).isZero();
        assertThat(secondStartWhileFirstRunning.get()).isEqualTo(1);
    }

    private static LakeOpsJob job() {
        LakeOpsJob job = new LakeOpsJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        job.setLakeId(UUID.randomUUID());
        job.setKind(LakeOpsJobKind.IMPORT);
        job.setStatus(LakeOpsJobStatus.QUEUED);
        return job;
    }
}
