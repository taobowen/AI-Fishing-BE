package com.aifishing.planning.spatial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "lake_fishing_target_samples")
public class LakeFishingTargetSample {

    @Id
    private UUID id;

    @Column(name = "spatial_planning_snapshot_id", nullable = false)
    private UUID spatialPlanningSnapshotId;

    @Column(name = "fishing_target_id", nullable = false)
    private UUID fishingTargetId;

    @Column(nullable = false)
    private BigDecimal fraction;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "depth_m")
    private BigDecimal depthM;

    @Column(name = "slope_deg")
    private BigDecimal slopeDeg;

    @Column(name = "aspect_deg")
    private BigDecimal aspectDeg;

    @Column(name = "orientation_deg")
    private BigDecimal orientationDeg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "static_factors", columnDefinition = "jsonb")
    private Map<String, Object> staticFactors = Map.of();

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

    public UUID getSpatialPlanningSnapshotId() {
        return spatialPlanningSnapshotId;
    }

    public void setSpatialPlanningSnapshotId(UUID spatialPlanningSnapshotId) {
        this.spatialPlanningSnapshotId = spatialPlanningSnapshotId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public BigDecimal getFraction() {
        return fraction;
    }

    public void setFraction(BigDecimal fraction) {
        this.fraction = fraction;
    }

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public BigDecimal getDepthM() {
        return depthM;
    }

    public void setDepthM(BigDecimal depthM) {
        this.depthM = depthM;
    }

    public BigDecimal getSlopeDeg() {
        return slopeDeg;
    }

    public void setSlopeDeg(BigDecimal slopeDeg) {
        this.slopeDeg = slopeDeg;
    }

    public BigDecimal getAspectDeg() {
        return aspectDeg;
    }

    public void setAspectDeg(BigDecimal aspectDeg) {
        this.aspectDeg = aspectDeg;
    }

    public BigDecimal getOrientationDeg() {
        return orientationDeg;
    }

    public void setOrientationDeg(BigDecimal orientationDeg) {
        this.orientationDeg = orientationDeg;
    }

    public Map<String, Object> getStaticFactors() {
        return staticFactors;
    }

    public void setStaticFactors(Map<String, Object> staticFactors) {
        this.staticFactors = staticFactors == null ? Map.of() : Map.copyOf(staticFactors);
    }
}
