package com.aifishing.planning.spatial;

import java.util.List;
import java.util.UUID;

/**
 * Generate-time connected executable subset of one PhysicalZone. Not a lake_fishing_zones row.
 */
public record ZoneVisitScope(
        UUID id,
        UUID physicalZoneId,
        List<UUID> memberTargetIds,
        String label
) {
    public ZoneVisitScope {
        memberTargetIds = memberTargetIds == null ? List.of() : List.copyOf(memberTargetIds);
        label = label == null ? "" : label;
    }
}
