package com.aifishing.planning.spatial;

/**
 * Runtime grouping only. Not a persisted snapshot entity.
 */
public final class CastingOpportunity {

    public static final String ANCHOR_REASON = "zone micro target";
    public static final String COMPANION_REASON = "same casting opportunity";

    private CastingOpportunity() {
    }

    public static boolean companionReason(String reason) {
        return COMPANION_REASON.equals(reason);
    }
}
