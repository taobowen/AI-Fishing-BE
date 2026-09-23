package com.aifishing.planning.search;

public record SearchDiagnostics(
        int usableMinutes,
        int representativeDwellMinutes,
        double representativeInterStopMinutes,
        int expectedStops,
        int conservativeDwellMinutes,
        double optimisticInterStopMinutes,
        int maxFeasibleStops,
        int effectiveMaxStops,
        SearchModeReason modeReason
) {
}
