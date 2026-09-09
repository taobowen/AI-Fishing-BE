package com.aifishing.planning.route;

import org.locationtech.jts.geom.Point;

import java.util.UUID;

public record AccessResolution(
        AccessStatus status,
        UUID accessPointId,
        String name,
        Point location
) {
    public enum AccessStatus {
        SELECTED,
        AUTHORITATIVE,
        UNKNOWN
    }

    public static AccessResolution unknown() {
        return new AccessResolution(AccessStatus.UNKNOWN, null, null, null);
    }

    public boolean known() {
        return status != AccessStatus.UNKNOWN && location != null;
    }
}
