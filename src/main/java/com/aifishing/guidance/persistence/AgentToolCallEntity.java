package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.ToolName;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_tool_calls")
public class AgentToolCallEntity extends GuidanceCreatedEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Convert(converter = ToolNameConverter.class)
    @Column(name = "tool_name", nullable = false, length = 64)
    private ToolName toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> request;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public ToolName getToolName() {
        return toolName;
    }

    public void setToolName(ToolName toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getRequest() {
        return request;
    }

    public void setRequest(Map<String, Object> request) {
        this.request = request;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }
}
