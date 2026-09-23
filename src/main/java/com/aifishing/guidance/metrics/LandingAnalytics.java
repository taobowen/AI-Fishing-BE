package com.aifishing.guidance.metrics;

/**
 * Landing is a separate analytic, not a {@code GuidanceSuccessKind} member.
 * FISH_ON / CATCH_LOST remain guidance success when followed.
 */
public record LandingAnalytics(
        int landedCount,
        int lostCount
) {
}
