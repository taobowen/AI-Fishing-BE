package com.aifishing.planning.spatial;

/**
 * Shared wire enum. Atomic lake_fishing_targets may only persist POINT or PATH.
 * ZONE is a visit/container kind, never an atomic FishingTargetKind.
 * SEGMENT and AREA remain read-only compatibility values.
 */
public enum TargetKind {
    POINT,
    PATH,
    SEGMENT,
    AREA,
    ZONE;

    public boolean isAtomicTarget() {
        return this == POINT || this == PATH || this == SEGMENT;
    }

    public boolean isPathLike() {
        return this == PATH || this == SEGMENT || this == AREA;
    }

    public boolean isZoneVisit() {
        return this == ZONE;
    }

    public boolean allowedOnLakeFishingTargets() {
        return this == POINT || this == PATH || this == SEGMENT || this == AREA;
    }

    public TargetKind persistedTargetKind() {
        if (this == SEGMENT || this == AREA) {
            return PATH;
        }
        if (this == ZONE) {
            throw new IllegalStateException("ZONE is not an atomic FishingTargetKind");
        }
        return this;
    }

    /** Atomic lake_fishing_targets kinds. ZONE is a visit/container only. */
    public static java.util.List<TargetKind> atomicTargetKinds() {
        return java.util.List.of(POINT, PATH);
    }
}
