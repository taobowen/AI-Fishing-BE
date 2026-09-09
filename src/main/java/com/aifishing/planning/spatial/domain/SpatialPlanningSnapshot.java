package com.aifishing.planning.spatial.domain;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "spatial_planning_snapshots")
public class SpatialPlanningSnapshot {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline", nullable = false)
    private Pipeline featurePipeline;

    @Column(name = "feature_analysis_version", nullable = false)
    private String featureAnalysisVersion;

    @Column(name = "target_derivation_version", nullable = false)
    private String targetDerivationVersion;

    @Column(name = "zone_builder_version", nullable = false)
    private String zoneBuilderVersion;

    @Column(name = "navigation_version", nullable = false)
    private String navigationVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpatialSnapshotStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> timings = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> counts = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "navigation_grid", columnDefinition = "jsonb")
    private Map<String, Object> navigationGrid = new LinkedHashMap<>();

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = SpatialSnapshotStatus.RUNNING;
        }
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

    public String getTargetDerivationVersion() {
        return targetDerivationVersion;
    }

    public void setTargetDerivationVersion(String targetDerivationVersion) {
        this.targetDerivationVersion = targetDerivationVersion;
    }

    public String getZoneBuilderVersion() {
        return zoneBuilderVersion;
    }

    public void setZoneBuilderVersion(String zoneBuilderVersion) {
        this.zoneBuilderVersion = zoneBuilderVersion;
    }

    public String getNavigationVersion() {
        return navigationVersion;
    }

    public void setNavigationVersion(String navigationVersion) {
        this.navigationVersion = navigationVersion;
    }

    public SpatialSnapshotStatus getStatus() {
        return status;
    }

    public void setStatus(SpatialSnapshotStatus status) {
        this.status = status;
    }

    public Map<String, Object> getTimings() {
        return timings;
    }

    public void setTimings(Map<String, Object> timings) {
        this.timings = timings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(timings);
    }

    public Map<String, Object> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Object> counts) {
        this.counts = counts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(counts);
    }

    public Map<String, Object> getNavigationGrid() {
        return navigationGrid;
    }

    public void setNavigationGrid(Map<String, Object> navigationGrid) {
        this.navigationGrid = navigationGrid == null ? new LinkedHashMap<>() : new LinkedHashMap<>(navigationGrid);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}
