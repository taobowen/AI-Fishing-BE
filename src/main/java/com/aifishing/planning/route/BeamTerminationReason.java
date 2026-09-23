package com.aifishing.planning.route;

/**
 * Why Beam Search stopped expanding. Diagnostic only — does not affect the chosen route.
 */
public enum BeamTerminationReason {
    NO_SUCCESSORS,
    MAX_STOPS_REACHED,
    TRIP_WINDOW_EXHAUSTED,
    EXPANSION_BUDGET_EXCEEDED,
    BEAM_COMPLETED_NORMALLY
}
