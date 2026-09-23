package com.aifishing.lake.ops;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.ops.jobs.launcher", havingValue = "inline", matchIfMissing = true)
public class InlineLakeOpsJobLauncher implements LakeOpsJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(InlineLakeOpsJobLauncher.class);

    private final LakeOpsJobExecutor executor;
    private final ThreadPoolExecutor worker;

    /**
     * In-process only. This 1-thread pool is <strong>not</strong> the ECS concurrency proof;
     * production {@code launcher=ecs} uses {@link EcsLakeOpsJobLauncher} +
     * {@code app.ops.jobs.ecs.max-concurrent}.
     */
    public InlineLakeOpsJobLauncher(LakeOpsJobExecutor executor) {
        this.executor = executor;
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable, "lake-ops-inline");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void afterQueued(LakeOpsJob job) {
        worker.execute(() -> {
            try {
                executor.claimAndRun(job.getId());
            } catch (RuntimeException ex) {
                log.warn("inline lake ops job {} failed: {}", job.getId(), ex.getMessage());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException ex) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
