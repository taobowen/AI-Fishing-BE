package com.aifishing.planning.search;

public record SearchParameters(
        SearchMode mode,
        int beamWidth,
        int maxStops,
        int minStops,
        int lookaheadHorizon,
        int expansionBudget,
        int commitPrefixStops,
        double futurePotentialLambda,
        SearchDiagnostics diagnostics,
        long maxWallClockMs
) {
    public SearchParameters(
            SearchMode mode,
            int beamWidth,
            int maxStops,
            int minStops,
            int lookaheadHorizon,
            int expansionBudget,
            int commitPrefixStops,
            double futurePotentialLambda
    ) {
        this(
                mode,
                beamWidth,
                maxStops,
                minStops,
                lookaheadHorizon,
                expansionBudget,
                commitPrefixStops,
                futurePotentialLambda,
                null,
                0
        );
    }

    public int expectedStops() {
        return diagnostics == null ? maxStops : diagnostics.expectedStops();
    }

    public int maxFeasibleStops() {
        return diagnostics == null ? maxStops : diagnostics.maxFeasibleStops();
    }

    public SearchModeReason modeReason() {
        return diagnostics == null
                ? (mode == SearchMode.ROLLING_HORIZON
                ? SearchModeReason.ROLLING_FULL_ROUTE_BUDGET_EXCEEDED
                : SearchModeReason.FULL_ROUTE)
                : diagnostics.modeReason();
    }
}
