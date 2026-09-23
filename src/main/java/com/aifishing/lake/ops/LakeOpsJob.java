package com.aifishing.lake.ops;

import com.aifishing.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lake_ops_jobs")
public class LakeOpsJob extends AuditedEntity {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LakeOpsJobKind kind;

    @Column(name = "dedupe_key", nullable = false, length = 256)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LakeOpsJobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 64)
    private LakeOpsFailureCode failureCode;

    @Column(name = "ecs_task_arn")
    private String ecsTaskArn;

    @Column(name = "launch_attempted_at")
    private Instant launchAttemptedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Override
    public UUID id() {
        return id;
    }

    @Override
    protected void assignId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLakeId() {
        return lakeId;
    }

    public void setLakeId(UUID lakeId) {
        this.lakeId = lakeId;
    }

    public LakeOpsJobKind getKind() {
        return kind;
    }

    public void setKind(LakeOpsJobKind kind) {
        this.kind = kind;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : params;
    }

    public LakeOpsJobStatus getStatus() {
        return status;
    }

    public void setStatus(LakeOpsJobStatus status) {
        this.status = status;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LakeOpsFailureCode getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(LakeOpsFailureCode failureCode) {
        this.failureCode = failureCode;
    }

    public Instant getLaunchAttemptedAt() {
        return launchAttemptedAt;
    }

    public void setLaunchAttemptedAt(Instant launchAttemptedAt) {
        this.launchAttemptedAt = launchAttemptedAt;
    }

    public String getEcsTaskArn() {
        return ecsTaskArn;
    }

    public void setEcsTaskArn(String ecsTaskArn) {
        this.ecsTaskArn = ecsTaskArn;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
