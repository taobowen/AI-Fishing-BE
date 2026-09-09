package com.aifishing.planning.spatial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lake_fishing_nav_edges")
public class LakeFishingNavEdge {

    @Id
    private UUID id;

    @Column(name = "spatial_planning_snapshot_id", nullable = false)
    private UUID spatialPlanningSnapshotId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "from_node_id", nullable = false)
    private UUID fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private UUID toNodeId;

    @Column(nullable = false)
    private BigDecimal meters;

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

    public UUID getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(UUID fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public UUID getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(UUID toNodeId) {
        this.toNodeId = toNodeId;
    }

    public BigDecimal getMeters() {
        return meters;
    }

    public void setMeters(BigDecimal meters) {
        this.meters = meters;
    }
}
