package com.aifishing.planning.spatial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lake_navigation_tiles")
public class LakeNavigationTile {

    @Id
    private UUID id;

    @Column(name = "spatial_planning_snapshot_id", nullable = false)
    private UUID spatialPlanningSnapshotId;

    @Column(name = "tile_x", nullable = false)
    private int tileX;

    @Column(name = "tile_y", nullable = false)
    private int tileY;

    @Column(name = "traversability_mask", nullable = false)
    private byte[] traversabilityMask;

    @Column(name = "clearance_m")
    private byte[] clearanceM;

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

    public int getTileX() {
        return tileX;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public byte[] getTraversabilityMask() {
        return traversabilityMask;
    }

    public void setTraversabilityMask(byte[] traversabilityMask) {
        this.traversabilityMask = traversabilityMask;
    }

    public byte[] getClearanceM() {
        return clearanceM;
    }

    public void setClearanceM(byte[] clearanceM) {
        this.clearanceM = clearanceM;
    }
}
