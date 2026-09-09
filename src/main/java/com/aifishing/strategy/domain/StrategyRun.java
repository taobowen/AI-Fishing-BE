package com.aifishing.strategy.domain;

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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trip_strategy_runs")
public class StrategyRun {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyRunStatus status = StrategyRunStatus.PENDING;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline", nullable = false)
    private Pipeline featurePipeline = Pipeline.GIS;

    @Column(name = "feature_analysis_version")
    private String featureAnalysisVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fishing_context", columnDefinition = "jsonb")
    private Map<String, Object> fishingContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weather_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> weatherSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strategy_profile", columnDefinition = "jsonb")
    private Map<String, Object> strategyProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata", columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_metadata", columnDefinition = "jsonb")
    private Map<String, Object> usageMetadata;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
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

    public StrategyRunStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyRunStatus status) {
        this.status = status;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
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

    public Map<String, Object> getFishingContext() {
        return fishingContext;
    }

    public void setFishingContext(Map<String, Object> fishingContext) {
        this.fishingContext = fishingContext;
    }

    public Map<String, Object> getWeatherSnapshot() {
        return weatherSnapshot;
    }

    public void setWeatherSnapshot(Map<String, Object> weatherSnapshot) {
        this.weatherSnapshot = weatherSnapshot;
    }

    public Map<String, Object> getStrategyProfile() {
        return strategyProfile;
    }

    public void setStrategyProfile(Map<String, Object> strategyProfile) {
        this.strategyProfile = strategyProfile;
    }

    public Map<String, Object> getSourceMetadata() {
        return sourceMetadata;
    }

    public void setSourceMetadata(Map<String, Object> sourceMetadata) {
        this.sourceMetadata = sourceMetadata;
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
}
