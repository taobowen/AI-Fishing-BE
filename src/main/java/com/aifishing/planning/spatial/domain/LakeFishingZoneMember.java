package com.aifishing.planning.spatial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lake_fishing_zone_members")
@IdClass(LakeFishingZoneMemberId.class)
public class LakeFishingZoneMember {

    @Id
    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Id
    @Column(name = "fishing_target_id", nullable = false)
    private UUID fishingTargetId;

    @Column(nullable = false)
    private int sequence;

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }
}
