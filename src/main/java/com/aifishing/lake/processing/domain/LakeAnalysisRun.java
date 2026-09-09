package com.aifishing.lake.processing.domain;

import com.aifishing.lake.processing.dto.AnalysisRunStatus;
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
@Table(name = "lake_analysis_runs")
public class LakeAnalysisRun {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Column(name = "analysis_version", nullable = false)
    private String analysisVersion;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pipeline pipeline = Pipeline.GIS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisRunStatus status = AnalysisRunStatus.RUNNING;

    @Column(name = "gis_parent_run_id")
    private UUID gisParentRunId;

    @Column(name = "gis_analysis_version")
    private String gisAnalysisVersion;

    @Column(name = "vision_parent_run_id")
    private UUID visionParentRunId;

    @Column(name = "vision_analysis_version")
    private String visionAnalysisVersion;

    @Column(name = "source_snapshot_id")
    private String sourceSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_dataset_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> sourceDatasetSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_counts", columnDefinition = "jsonb")
    private Map<String, Object> featureCounts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_summary", columnDefinition = "jsonb")
    private Map<String, Object> errorSummary;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
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

    public String getAnalysisVersion() {
        return analysisVersion;
    }

    public void setAnalysisVersion(String analysisVersion) {
        this.analysisVersion = analysisVersion;
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

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public AnalysisRunStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisRunStatus status) {
        this.status = status;
    }

    public UUID getGisParentRunId() {
        return gisParentRunId;
    }

    public void setGisParentRunId(UUID gisParentRunId) {
        this.gisParentRunId = gisParentRunId;
    }

    public String getGisAnalysisVersion() {
        return gisAnalysisVersion;
    }

    public void setGisAnalysisVersion(String gisAnalysisVersion) {
        this.gisAnalysisVersion = gisAnalysisVersion;
    }

    public UUID getVisionParentRunId() {
        return visionParentRunId;
    }

    public void setVisionParentRunId(UUID visionParentRunId) {
        this.visionParentRunId = visionParentRunId;
    }

    public String getVisionAnalysisVersion() {
        return visionAnalysisVersion;
    }

    public void setVisionAnalysisVersion(String visionAnalysisVersion) {
        this.visionAnalysisVersion = visionAnalysisVersion;
    }

    public String getSourceSnapshotId() {
        return sourceSnapshotId;
    }

    public void setSourceSnapshotId(String sourceSnapshotId) {
        this.sourceSnapshotId = sourceSnapshotId;
    }

    public Map<String, Object> getSourceDatasetSnapshot() {
        return sourceDatasetSnapshot;
    }

    public void setSourceDatasetSnapshot(Map<String, Object> sourceDatasetSnapshot) {
        this.sourceDatasetSnapshot = sourceDatasetSnapshot;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getFeatureCounts() {
        return featureCounts;
    }

    public void setFeatureCounts(Map<String, Object> featureCounts) {
        this.featureCounts = featureCounts;
    }

    public Map<String, Object> getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(Map<String, Object> errorSummary) {
        this.errorSummary = errorSummary;
    }
}
