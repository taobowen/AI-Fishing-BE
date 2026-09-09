package com.aifishing.planning.spatial.domain;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.TargetKind;
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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lake_fishing_targets")
public class LakeFishingTarget {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline", nullable = false)
    private Pipeline featurePipeline;

    @Column(name = "feature_analysis_version", nullable = false)
    private String featureAnalysisVersion;

    @Column(name = "derivation_version", nullable = false)
    private String derivationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false)
    private TargetKind targetKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_type", nullable = false)
    private FeatureType semanticType;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "representative_point", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point representativePoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "fishing_corridor", columnDefinition = "geometry(Geometry,4326)")
    private Geometry fishingCorridor;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "entry_point", columnDefinition = "geometry(Point,4326)")
    private Point entryPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "exit_point", columnDefinition = "geometry(Point,4326)")
    private Point exitPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "selected_fishing_path", columnDefinition = "geometry(LineString,4326)")
    private LineString selectedFishingPath;

    @Column(name = "fishing_corridor_width_m")
    private BigDecimal fishingCorridorWidthM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_feature_ids", columnDefinition = "jsonb")
    private List<UUID> sourceFeatureIds = List.of();

    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column(name = "spatial_planning_snapshot_id")
    private UUID spatialPlanningSnapshotId;

    @Column(name = "closed_loop", nullable = false)
    private boolean closedLoop;

    @Column(name = "path_topology")
    private String pathTopology;

    @Column(name = "chainage_start_m")
    private BigDecimal chainageStartM;

    @Column(name = "chainage_end_m")
    private BigDecimal chainageEndM;

    @Column(name = "split_reason")
    private String splitReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "static_metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> staticMetadata = java.util.Map.of();

    @Column(name = "min_depth_m")
    private BigDecimal minDepthM;

    @Column(name = "max_depth_m")
    private BigDecimal maxDepthM;

    @Column(name = "representative_depth_m")
    private BigDecimal representativeDepthM;

    @Column
    private BigDecimal confidence;

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

    public String getDerivationVersion() {
        return derivationVersion;
    }

    public void setDerivationVersion(String derivationVersion) {
        this.derivationVersion = derivationVersion;
    }

    public TargetKind getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public FeatureType getSemanticType() {
        return semanticType;
    }

    public void setSemanticType(FeatureType semanticType) {
        this.semanticType = semanticType;
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

    public Geometry getFishingCorridor() {
        return fishingCorridor;
    }

    public void setFishingCorridor(Geometry fishingCorridor) {
        this.fishingCorridor = fishingCorridor;
    }

    public Point getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(Point entryPoint) {
        this.entryPoint = entryPoint;
    }

    public Point getExitPoint() {
        return exitPoint;
    }

    public void setExitPoint(Point exitPoint) {
        this.exitPoint = exitPoint;
    }

    public LineString getSelectedFishingPath() {
        return selectedFishingPath;
    }

    public void setSelectedFishingPath(LineString selectedFishingPath) {
        this.selectedFishingPath = selectedFishingPath;
    }

    public BigDecimal getFishingCorridorWidthM() {
        return fishingCorridorWidthM;
    }

    public void setFishingCorridorWidthM(BigDecimal fishingCorridorWidthM) {
        this.fishingCorridorWidthM = fishingCorridorWidthM;
    }

    public List<UUID> getSourceFeatureIds() {
        return sourceFeatureIds;
    }

    public void setSourceFeatureIds(List<UUID> sourceFeatureIds) {
        this.sourceFeatureIds = sourceFeatureIds == null ? List.of() : List.copyOf(sourceFeatureIds);
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public UUID getSpatialPlanningSnapshotId() {
        return spatialPlanningSnapshotId;
    }

    public void setSpatialPlanningSnapshotId(UUID spatialPlanningSnapshotId) {
        this.spatialPlanningSnapshotId = spatialPlanningSnapshotId;
    }

    public boolean isClosedLoop() {
        return closedLoop;
    }

    public void setClosedLoop(boolean closedLoop) {
        this.closedLoop = closedLoop;
    }

    public String getPathTopology() {
        return pathTopology;
    }

    public void setPathTopology(String pathTopology) {
        this.pathTopology = pathTopology;
    }

    public BigDecimal getChainageStartM() {
        return chainageStartM;
    }

    public void setChainageStartM(BigDecimal chainageStartM) {
        this.chainageStartM = chainageStartM;
    }

    public BigDecimal getChainageEndM() {
        return chainageEndM;
    }

    public void setChainageEndM(BigDecimal chainageEndM) {
        this.chainageEndM = chainageEndM;
    }

    public String getSplitReason() {
        return splitReason;
    }

    public void setSplitReason(String splitReason) {
        this.splitReason = splitReason;
    }

    public java.util.Map<String, Object> getStaticMetadata() {
        return staticMetadata;
    }

    public void setStaticMetadata(java.util.Map<String, Object> staticMetadata) {
        this.staticMetadata = staticMetadata == null ? java.util.Map.of() : java.util.Map.copyOf(staticMetadata);
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

    public BigDecimal getRepresentativeDepthM() {
        return representativeDepthM;
    }

    public void setRepresentativeDepthM(BigDecimal representativeDepthM) {
        this.representativeDepthM = representativeDepthM;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }
}
