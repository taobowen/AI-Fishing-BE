package com.aifishing.planning.spatial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lake_fishing_water_paths")
public class LakeFishingWaterPath {

    @Id
    private UUID id;

    @Column(name = "spatial_planning_snapshot_id", nullable = false)
    private UUID spatialPlanningSnapshotId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "from_key", nullable = false)
    private String fromKey;

    @Column(name = "to_key", nullable = false)
    private String toKey;

    @Column(nullable = false)
    private BigDecimal meters;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(LineString,4326)")
    private LineString path;

    @Column(nullable = false)
    private boolean precomputed;

    @Column(name = "navigation_version")
    private String navigationVersion;

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

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public String getFromKey() {
        return fromKey;
    }

    public void setFromKey(String fromKey) {
        this.fromKey = fromKey;
    }

    public String getToKey() {
        return toKey;
    }

    public void setToKey(String toKey) {
        this.toKey = toKey;
    }

    public BigDecimal getMeters() {
        return meters;
    }

    public void setMeters(BigDecimal meters) {
        this.meters = meters;
    }

    public LineString getPath() {
        return path;
    }

    public void setPath(LineString path) {
        this.path = path;
    }

    public boolean isPrecomputed() {
        return precomputed;
    }

    public void setPrecomputed(boolean precomputed) {
        this.precomputed = precomputed;
    }

    public String getNavigationVersion() {
        return navigationVersion;
    }

    public void setNavigationVersion(String navigationVersion) {
        this.navigationVersion = navigationVersion;
    }
}
