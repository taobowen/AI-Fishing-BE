package com.aifishing.planning.spatial;

import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zone water paths computed during one Generate Plan and not yet written.
 * Identity is {@code (snapshotId, zoneId, fromKey, toKey)}. Lifetime matches
 * {@link com.aifishing.planning.service.PlanningContext}.
 */
public final class PendingZoneWaterPaths {

    private final Map<String, Row> rows = new LinkedHashMap<>();

    public boolean add(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            String navigationVersion
    ) {
        if (snapshotId == null || zoneId == null || fromKey == null || toKey == null || estimate == null) {
            return false;
        }
        String key = key(snapshotId, zoneId, fromKey, toKey);
        if (rows.containsKey(key)) {
            return false;
        }
        rows.put(key, new Row(snapshotId, zoneId, fromKey, toKey, estimate, navigationVersion));
        return true;
    }

    public int size() {
        return rows.size();
    }

    public List<Row> drain() {
        List<Row> copy = new ArrayList<>(rows.values());
        rows.clear();
        return copy;
    }

    public static String key(UUID snapshotId, UUID zoneId, String fromKey, String toKey) {
        return snapshotId + "|" + zoneId + "|" + fromKey + "|" + toKey;
    }

    public record Row(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey,
            LocalWaterPathEstimator.PathEstimate estimate,
            String navigationVersion
    ) {
        public LakeFishingWaterPath toEntity() {
            LakeFishingWaterPath row = new LakeFishingWaterPath();
            row.setSpatialPlanningSnapshotId(snapshotId);
            row.setZoneId(zoneId);
            row.setFromKey(fromKey);
            row.setToKey(toKey);
            row.setMeters(BigDecimal.valueOf(estimate.meters()));
            row.setPath(estimate.path());
            row.setPrecomputed(false);
            row.setNavigationVersion(navigationVersion);
            return row;
        }
    }
}
