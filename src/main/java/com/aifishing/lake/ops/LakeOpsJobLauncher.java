package com.aifishing.lake.ops;

public interface LakeOpsJobLauncher {

    void afterQueued(LakeOpsJob job);

    /**
     * ECS only: start the oldest slot-wait {@code QUEUED} jobs when a concurrent slot frees.
     * Inline is a 1-thread pool and does not queue on ARN/slots.
     */
    default void launchWaitingJobs() {
    }
}
