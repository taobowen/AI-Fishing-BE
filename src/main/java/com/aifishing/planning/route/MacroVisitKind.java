package com.aifishing.planning.route;

/**
 * How a fishing action relates to opportunity consumption.
 * EXTEND replaces the current stop’s package and is not a new macro visit.
 * REVISIT is only used after the route has left the zone and later returns.
 */
public enum MacroVisitKind {
    NEW_ZONE_VISIT,
    EXTEND_CURRENT_ZONE,
    REVISIT_PARTIALLY_CONSUMED_ZONE,
    NEW_ATOMIC,
    EXTEND_CURRENT_ATOMIC;

    public boolean isExtend() {
        return this == EXTEND_CURRENT_ZONE || this == EXTEND_CURRENT_ATOMIC;
    }

    public boolean isZoneEntry() {
        return this == NEW_ZONE_VISIT || this == REVISIT_PARTIALLY_CONSUMED_ZONE;
    }
}
