package com.aifishing.planning.route;

/**
 * Why a Beam Search successor was dropped. Diagnostic only — does not affect search.
 */
public enum BeamRejectReason {
    VISIT_KIND_NULL,
    MAX_STOPS,
    SPACING,
    UNKNOWN_TRAVEL,
    OUTBOUND_WEATHER_HARD_REJECT,
    DEPARTURE_AFTER_TRIP_END,
    RETURN_RESERVE,
    RETURN_WEATHER_HARD_REJECT,
    LEG_TOO_LONG,
    RANGE_EXCEEDED,
    ARRIVAL_WEATHER_HARD_REJECT,
    NON_POSITIVE_INCREMENT,
    REVISIT_NO_NEW_MEMBERS,
    EXTEND_WAIT_NOT_ALLOWED,
    DOMINANCE_PRUNED,
    BEAM_WIDTH_PRUNED,
    EXPANSION_BUDGET_EXCEEDED
}
