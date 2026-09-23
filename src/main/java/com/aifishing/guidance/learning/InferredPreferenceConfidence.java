package com.aifishing.guidance.learning;

import java.time.Duration;
import java.time.Instant;

/**
 * Evidence-based confidence with recency decay. A single observation stays below
 * the default hard-constraint threshold.
 */
public final class InferredPreferenceConfidence {

    static final double EVIDENCE_SCALE = 3.0;

    private InferredPreferenceConfidence() {
    }

    public static double fromEvidence(int evidenceCount) {
        int count = Math.max(0, evidenceCount);
        return clamp(1.0 - Math.exp(-count / EVIDENCE_SCALE));
    }

    public static double withRecency(int evidenceCount, Instant lastObservedAt, Instant now, int halfLifeDays) {
        double base = fromEvidence(evidenceCount);
        if (lastObservedAt == null || now == null) {
            return base;
        }
        int halfLife = Math.max(1, halfLifeDays);
        long ageDays = Math.max(0, Duration.between(lastObservedAt, now).toDays());
        double recency = Math.exp(-Math.log(2) * ageDays / halfLife);
        return clamp(base * recency);
    }

    public static boolean meetsThreshold(double confidence, double threshold) {
        return confidence + 1e-9 >= threshold;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }
}
