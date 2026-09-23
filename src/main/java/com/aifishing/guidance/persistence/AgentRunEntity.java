package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 64)
    private GuidanceTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentRunVisibility visibility = AgentRunVisibility.PRODUCTION;

    @Column(name = "trigger_outbox_id")
    private UUID triggerOutboxId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_triggers", nullable = false, columnDefinition = "jsonb")
    private List<GuidanceTrigger> relatedTriggers = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_reason_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> triggerReasonCodes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> stateSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> contextSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory_ref_ids", columnDefinition = "jsonb")
    private List<String> memoryRefIds;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "tool_schema_version", length = 64)
    private String toolSchemaVersion;

    @Column(name = "context_version", length = 64)
    private String contextVersion;

    @Column(name = "agent_policy_version", length = 64)
    private String agentPolicyVersion;

    @Column(name = "learning_algorithm_version", length = 64)
    private String learningAlgorithmVersion;

    @Column(name = "learning_snapshot_version", length = 64)
    private String learningSnapshotVersion;

    @Column(name = "guidance_plan_version")
    private Integer guidancePlanVersion;

    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "fallback_reason", length = 64)
    private String fallbackReason;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_provider_usage", columnDefinition = "jsonb")
    private Map<String, Object> rawProviderUsage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_telemetry", columnDefinition = "jsonb")
    private Map<String, Object> usageTelemetry;

    @PrePersist
    void ensureReplayColumns() {
        if (visibility == null) {
            visibility = AgentRunVisibility.PRODUCTION;
        }
        if (relatedTriggers == null) {
            relatedTriggers = List.of();
        }
        if (triggerReasonCodes == null) {
            triggerReasonCodes = List.of();
        }
    }

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public GuidanceTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(GuidanceTrigger trigger) {
        this.trigger = trigger;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentRunStatus status) {
        this.status = status;
    }

    public AgentRunVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(AgentRunVisibility visibility) {
        this.visibility = visibility == null ? AgentRunVisibility.PRODUCTION : visibility;
    }

    public UUID getTriggerOutboxId() {
        return triggerOutboxId;
    }

    public void setTriggerOutboxId(UUID triggerOutboxId) {
        this.triggerOutboxId = triggerOutboxId;
    }

    public List<GuidanceTrigger> getRelatedTriggers() {
        return relatedTriggers;
    }

    public void setRelatedTriggers(List<GuidanceTrigger> relatedTriggers) {
        this.relatedTriggers = relatedTriggers == null ? List.of() : relatedTriggers;
    }

    public List<String> getTriggerReasonCodes() {
        return triggerReasonCodes;
    }

    public void setTriggerReasonCodes(List<String> triggerReasonCodes) {
        this.triggerReasonCodes = triggerReasonCodes == null ? List.of() : triggerReasonCodes;
    }

    public Map<String, Object> getStateSnapshot() {
        return stateSnapshot;
    }

    public void setStateSnapshot(Map<String, Object> stateSnapshot) {
        this.stateSnapshot = stateSnapshot;
    }

    public Map<String, Object> getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(Map<String, Object> contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }

    public List<String> getMemoryRefIds() {
        return memoryRefIds;
    }

    public void setMemoryRefIds(List<String> memoryRefIds) {
        this.memoryRefIds = memoryRefIds;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getToolSchemaVersion() {
        return toolSchemaVersion;
    }

    public void setToolSchemaVersion(String toolSchemaVersion) {
        this.toolSchemaVersion = toolSchemaVersion;
    }

    public String getContextVersion() {
        return contextVersion;
    }

    public void setContextVersion(String contextVersion) {
        this.contextVersion = contextVersion;
    }

    public String getAgentPolicyVersion() {
        return agentPolicyVersion;
    }

    public void setAgentPolicyVersion(String agentPolicyVersion) {
        this.agentPolicyVersion = agentPolicyVersion;
    }

    public String getLearningAlgorithmVersion() {
        return learningAlgorithmVersion;
    }

    public void setLearningAlgorithmVersion(String learningAlgorithmVersion) {
        this.learningAlgorithmVersion = learningAlgorithmVersion;
    }

    public String getLearningSnapshotVersion() {
        return learningSnapshotVersion;
    }

    public void setLearningSnapshotVersion(String learningSnapshotVersion) {
        this.learningSnapshotVersion = learningSnapshotVersion;
    }

    public Integer getGuidancePlanVersion() {
        return guidancePlanVersion;
    }

    public void setGuidancePlanVersion(Integer guidancePlanVersion) {
        this.guidancePlanVersion = guidancePlanVersion;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(UUID decisionId) {
        this.decisionId = decisionId;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public Map<String, Object> getRawProviderUsage() {
        return rawProviderUsage;
    }

    public void setRawProviderUsage(Map<String, Object> rawProviderUsage) {
        this.rawProviderUsage = rawProviderUsage;
    }

    public Map<String, Object> getUsageTelemetry() {
        return usageTelemetry;
    }

    public void setUsageTelemetry(Map<String, Object> usageTelemetry) {
        this.usageTelemetry = usageTelemetry;
    }
}
