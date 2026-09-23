package com.aifishing.lake.ops;

import java.util.Optional;
import java.util.UUID;

public interface EcsOpsClient {

    String runTask(UUID jobId);

    Optional<EcsTaskSnapshot> describe(String taskArn);

    record EcsTaskSnapshot(
            String lastStatus,
            String desiredStatus,
            String stopCode,
            String stoppedReason,
            Integer exitCode,
            String containerReason
    ) {
        boolean stopped() {
            return "STOPPED".equalsIgnoreCase(lastStatus);
        }

        String failureMessage() {
            StringBuilder out = new StringBuilder("ECS task STOPPED");
            if (stopCode != null && !stopCode.isBlank()) {
                out.append(" stopCode=").append(stopCode);
            }
            if (stoppedReason != null && !stoppedReason.isBlank()) {
                out.append(" stoppedReason=").append(stoppedReason);
            }
            if (exitCode != null) {
                out.append(" exitCode=").append(exitCode);
            }
            if (containerReason != null && !containerReason.isBlank()) {
                out.append(" containerReason=").append(containerReason);
            }
            return out.toString();
        }
    }
}
