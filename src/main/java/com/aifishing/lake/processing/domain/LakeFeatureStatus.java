package com.aifishing.lake.processing.domain;

import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lake_feature_status")
public class LakeFeatureStatus {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false)
    private FeatureType featureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pipeline pipeline = Pipeline.GIS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureStatusCode status = FeatureStatusCode.NOT_CHECKED;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "last_successful_analysis_at")
    private Instant lastSuccessfulAnalysisAt;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLakeId() {
        return lakeId;
    }

    public void setLakeId(UUID lakeId) {
        this.lakeId = lakeId;
    }

    public FeatureType getFeatureType() {
        return featureType;
    }

    public void setFeatureType(FeatureType featureType) {
        this.featureType = featureType;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public FeatureStatusCode getStatus() {
        return status;
    }

    public void setStatus(FeatureStatusCode status) {
        this.status = status;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(Instant lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public Instant getLastSuccessfulAnalysisAt() {
        return lastSuccessfulAnalysisAt;
    }

    public void setLastSuccessfulAnalysisAt(Instant lastSuccessfulAnalysisAt) {
        this.lastSuccessfulAnalysisAt = lastSuccessfulAnalysisAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
