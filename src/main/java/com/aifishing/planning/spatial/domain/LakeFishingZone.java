package com.aifishing.planning.spatial.domain;

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
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lake_fishing_zones")
public class LakeFishingZone {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline", nullable = false)
    private Pipeline featurePipeline;

    @Column(name = "feature_analysis_version", nullable = false)
    private String featureAnalysisVersion;

    @Column(name = "builder_version", nullable = false)
    private String builderVersion;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "representative_point", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point representativePoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clustering_params", columnDefinition = "jsonb")
    private Map<String, Object> clusteringParams;

    @Column(name = "spatial_planning_snapshot_id")
    private UUID spatialPlanningSnapshotId;

    @Column(name = "navigation_version")
    private String navigationVersion;

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

    public String getBuilderVersion() {
        return builderVersion;
    }

    public void setBuilderVersion(String builderVersion) {
        this.builderVersion = builderVersion;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Point getRepresentativePoint() {
        return representativePoint;
    }

    public void setRepresentativePoint(Point representativePoint) {
        this.representativePoint = representativePoint;
    }

    public Map<String, Object> getClusteringParams() {
        return clusteringParams;
    }

    public void setClusteringParams(Map<String, Object> clusteringParams) {
        this.clusteringParams = clusteringParams;
    }

    public UUID getSpatialPlanningSnapshotId() {
        return spatialPlanningSnapshotId;
    }

    public void setSpatialPlanningSnapshotId(UUID spatialPlanningSnapshotId) {
        this.spatialPlanningSnapshotId = spatialPlanningSnapshotId;
    }

    public String getNavigationVersion() {
        return navigationVersion;
    }

    public void setNavigationVersion(String navigationVersion) {
        this.navigationVersion = navigationVersion;
    }
}
