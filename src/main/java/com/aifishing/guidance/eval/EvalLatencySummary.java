package com.aifishing.guidance.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wall-clock observation only. Platform CI must not fail on these values.
 */
public record EvalLatencySummary(
        Integer sampleCount,
        Long p50Ms,
        Long p95Ms
) {
    public static EvalLatencySummary record(List<Long> wallClockMs) {
        if (wallClockMs == null || wallClockMs.isEmpty()) {
            return new EvalLatencySummary(0, null, null);
        }
        List<Long> sorted = new ArrayList<>();
        for (Long value : wallClockMs) {
            if (value != null && value >= 0) {
                sorted.add(value);
            }
        }
        if (sorted.isEmpty()) {
            return new EvalLatencySummary(0, null, null);
        }
        Collections.sort(sorted);
        return new EvalLatencySummary(sorted.size(), percentile(sorted, 0.50), percentile(sorted, 0.95));
    }

    private static long percentile(List<Long> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
