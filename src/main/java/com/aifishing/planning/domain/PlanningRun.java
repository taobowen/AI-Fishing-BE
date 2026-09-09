package com.aifishing.planning.domain;

import com.aifishing.common.enums.ClientChannel;
import com.aifishing.lake.processing.dto.Pipeline;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trip_planning_runs")
public class PlanningRun {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "strategy_run_id")
    private UUID strategyRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanningRunStatus status = PlanningRunStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline")
    private Pipeline featurePipeline;

    @Column(name = "feature_analysis_version")
    private String featureAnalysisVersion;

    @Column(name = "algorithm_version")
    private String algorithmVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ranking_config", columnDefinition = "jsonb")
    private Map<String, Object> rankingConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_summary", columnDefinition = "jsonb")
    private Map<String, Object> filterSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_metadata", columnDefinition = "jsonb")
    private Map<String, Object> usageMetadata;

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_channel")
    private ClientChannel clientChannel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getStrategyRunId() {
        return strategyRunId;
    }

    public void setStrategyRunId(UUID strategyRunId) {
        this.strategyRunId = strategyRunId;
    }

    public PlanningRunStatus getStatus() {
        return status;
    }

    public void setStatus(PlanningRunStatus status) {
        this.status = status;
    }

    public Pipeline getFeaturePipeline() {
        return featurePipeline;
    }

    public void setFeaturePipeline(Pipeline featurePipeline) {
        this.featurePipeline = featurePipeline;
    }

    public String getFeatureAnalysisVersion() {
        return featureAnalysisVersion;
    }

    public void setFeatureAnalysisVersion(String featureAnalysisVersion) {
        this.featureAnalysisVersion = featureAnalysisVersion;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Object> getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(Map<String, Object> inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public Map<String, Object> getRankingConfig() {
        return rankingConfig;
    }

    public void setRankingConfig(Map<String, Object> rankingConfig) {
        this.rankingConfig = rankingConfig;
    }

    public Map<String, Object> getFilterSummary() {
        return filterSummary;
    }

    public void setFilterSummary(Map<String, Object> filterSummary) {
        this.filterSummary = filterSummary;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Map<String, Object> getUsageMetadata() {
        return usageMetadata;
    }

    public void setUsageMetadata(Map<String, Object> usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ClientChannel getClientChannel() {
        return clientChannel;
    }

    public void setClientChannel(ClientChannel clientChannel) {
        this.clientChannel = clientChannel;
    }
}
