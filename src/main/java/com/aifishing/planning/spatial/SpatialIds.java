package com.aifishing.planning.spatial;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SpatialIds {

    private SpatialIds() {
    }

    public static UUID targetId(
            UUID snapshotId,
            UUID sourceFeatureId,
            int index,
            TargetKind kind,
            String fingerprint
    ) {
        return named("target", snapshotId, sourceFeatureId, index, kind == null ? "POINT" : kind.name(), fingerprint);
    }

    public static UUID zoneId(UUID snapshotId, String memberFingerprint) {
        return named("zone", snapshotId, null, 0, "ZONE", memberFingerprint);
    }

    public static UUID scopeId(UUID physicalZoneId, String memberFingerprint) {
        return named("scope", physicalZoneId, null, 0, "SCOPE", memberFingerprint);
    }

    public static UUID named(String kind, UUID root, UUID source, int index, String type, String fingerprint) {
        String raw = String.join(":",
                kind,
                String.valueOf(root),
                String.valueOf(source),
                Integer.toString(index),
                type == null ? "" : type,
                fingerprint == null ? "" : fingerprint);
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }
}
