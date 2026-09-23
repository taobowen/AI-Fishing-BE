package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;

public record OnlineOutcomeSnapshot(
        OnlineGuidanceMetrics metrics,
        LandingAnalytics landing,
        OnlineOutcomeRawCounts raw
) {
}
