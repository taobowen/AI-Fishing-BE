package com.aifishing.lake.ops;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LakeOpsJobResponse(
        UUID jobId,
        UUID lakeId,
        LakeOpsJobKind kind,
        LakeOpsJobStatus status,
        String dedupeKey,
        Map<String, Object> params,
        Map<String, Object> result,
        String errorMessage,
        LakeOpsFailureCode failureCode,
        String failureMessage,
        String ecsTaskArn,
        Instant heartbeatAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
    static LakeOpsJobResponse from(LakeOpsJob job) {
        return new LakeOpsJobResponse(
                job.getId(),
                job.getLakeId(),
                job.getKind(),
                job.getStatus(),
                job.getDedupeKey(),
                job.getParams(),
                job.getResult(),
                job.getErrorMessage(),
                job.getFailureCode(),
                job.getErrorMessage(),
                job.getEcsTaskArn(),
                job.getHeartbeatAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }
}
