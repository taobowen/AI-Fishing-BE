package com.aifishing.lake.processing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "derived_analysis_artifacts")
public class DerivedAnalysisArtifact {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "analysis_version", nullable = false)
    private String analysisVersion;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_dataset_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> sourceDatasetSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grid_metadata", columnDefinition = "jsonb")
    private Map<String, Object> gridMetadata;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Column(name = "storage_uri")
    private String storageUri;

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

    public UUID getLakeId() {
        return lakeId;
    }

    public void setLakeId(UUID lakeId) {
        this.lakeId = lakeId;
    }

    public UUID getAnalysisRunId() {
        return analysisRunId;
    }

    public void setAnalysisRunId(UUID analysisRunId) {
        this.analysisRunId = analysisRunId;
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

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getSourceDatasetSnapshot() {
        return sourceDatasetSnapshot;
    }

    public void setSourceDatasetSnapshot(Map<String, Object> sourceDatasetSnapshot) {
        this.sourceDatasetSnapshot = sourceDatasetSnapshot;
    }

    public Map<String, Object> getGridMetadata() {
        return gridMetadata;
    }

    public void setGridMetadata(Map<String, Object> gridMetadata) {
        this.gridMetadata = gridMetadata;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }
}
