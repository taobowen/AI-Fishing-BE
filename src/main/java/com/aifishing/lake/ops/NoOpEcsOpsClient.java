package com.aifishing.lake.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.ops.jobs.launcher", havingValue = "inline", matchIfMissing = true)
public class NoOpEcsOpsClient implements EcsOpsClient {

    @Override
    public String runTask(UUID jobId) {
        throw new IllegalStateException("ECS RunTask is disabled when app.ops.jobs.launcher=inline");
    }

    @Override
    public Optional<EcsTaskSnapshot> describe(String taskArn) {
        return Optional.empty();
    }
}
