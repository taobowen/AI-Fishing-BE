package com.aifishing.planning.spatial.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class LakeFishingZoneMemberId implements Serializable {

    private UUID zoneId;
    private UUID fishingTargetId;

    public LakeFishingZoneMemberId() {
    }

    public LakeFishingZoneMemberId(UUID zoneId, UUID fishingTargetId) {
        this.zoneId = zoneId;
        this.fishingTargetId = fishingTargetId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LakeFishingZoneMemberId that)) {
            return false;
        }
        return Objects.equals(zoneId, that.zoneId) && Objects.equals(fishingTargetId, that.fishingTargetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId, fishingTargetId);
    }
}
