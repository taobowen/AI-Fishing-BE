package com.aifishing.lake.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(name = "app.ops.jobs.launcher", havingValue = "ecs")
@EnableScheduling
public class LakeOpsReconcileConfig {

    private final LakeOpsReconciler reconciler;

    public LakeOpsReconcileConfig(LakeOpsReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${app.ops.jobs.reconcile-ms:30000}")
    public void reconcile() {
        reconciler.reconcileActive();
    }
}
