package com.aifishing.lake.processing.domain;

import com.aifishing.lake.processing.dto.FeatureType;
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
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lake_features")
public class LakeFeature {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pipeline pipeline = Pipeline.GIS;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @Column(name = "min_depth_m")
    private BigDecimal minDepthM;

    @Column(name = "max_depth_m")
    private BigDecimal maxDepthM;

    private BigDecimal slope;

    private BigDecimal orientation;

    @Column(name = "area_m2")
    private BigDecimal areaM2;

    @Column(nullable = false)
    private BigDecimal confidence;

    @Column(name = "source_method", nullable = false)
    private String sourceMethod;

    @Column(nullable = false)
    private String provider;

    @Column(name = "analysis_version", nullable = false)
    private String analysisVersion;

    @Column(name = "analysis_run_id")
    private UUID analysisRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_dataset_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> sourceDatasetSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "derivation_metadata", columnDefinition = "jsonb")
    private Map<String, Object> derivationMetadata;

    @Column(name = "source_record_id")
    private String sourceRecordId;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
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

    public FeatureType getType() {
        return type;
    }

    public void setType(FeatureType type) {
        this.type = type;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public BigDecimal getMinDepthM() {
        return minDepthM;
    }

    public void setMinDepthM(BigDecimal minDepthM) {
        this.minDepthM = minDepthM;
    }

    public BigDecimal getMaxDepthM() {
        return maxDepthM;
    }

    public void setMaxDepthM(BigDecimal maxDepthM) {
        this.maxDepthM = maxDepthM;
    }

    public BigDecimal getSlope() {
        return slope;
    }

    public void setSlope(BigDecimal slope) {
        this.slope = slope;
    }

    public BigDecimal getOrientation() {
        return orientation;
    }

    public void setOrientation(BigDecimal orientation) {
        this.orientation = orientation;
    }

    public BigDecimal getAreaM2() {
        return areaM2;
    }

    public void setAreaM2(BigDecimal areaM2) {
        this.areaM2 = areaM2;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(String sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAnalysisVersion() {
        return analysisVersion;
    }

    public void setAnalysisVersion(String analysisVersion) {
        this.analysisVersion = analysisVersion;
    }

    public UUID getAnalysisRunId() {
        return analysisRunId;
    }

    public void setAnalysisRunId(UUID analysisRunId) {
        this.analysisRunId = analysisRunId;
    }

    public Map<String, Object> getSourceDatasetSnapshot() {
        return sourceDatasetSnapshot;
    }

    public void setSourceDatasetSnapshot(Map<String, Object> sourceDatasetSnapshot) {
        this.sourceDatasetSnapshot = sourceDatasetSnapshot;
    }

    public Map<String, Object> getDerivationMetadata() {
        return derivationMetadata;
    }

    public void setDerivationMetadata(Map<String, Object> derivationMetadata) {
        this.derivationMetadata = derivationMetadata;
    }

    public String getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(String sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }
}
