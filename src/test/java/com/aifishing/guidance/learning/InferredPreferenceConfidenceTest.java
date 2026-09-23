package com.aifishing.guidance.learning;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InferredPreferenceConfidenceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");

    @Test
    void singleObservationStaysBelowHardConstraintThreshold() {
        double confidence = InferredPreferenceConfidence.fromEvidence(1);

        assertThat(confidence).isCloseTo(0.283, within(0.001));
        assertThat(InferredPreferenceConfidence.meetsThreshold(confidence, 0.5)).isFalse();
    }

    @Test
    void threeObservationsCrossDefaultThreshold() {
        double confidence = InferredPreferenceConfidence.fromEvidence(3);

        assertThat(confidence).isCloseTo(0.632, within(0.001));
        assertThat(InferredPreferenceConfidence.meetsThreshold(confidence, 0.5)).isTrue();
    }

    @Test
    void recencyDecaysOldEvidenceBelowThreshold() {
        Instant lastSeen = NOW.minusSeconds(90L * 24 * 3600);
        double confidence = InferredPreferenceConfidence.withRecency(5, lastSeen, NOW, 45);

        assertThat(confidence).isLessThan(0.5);
        assertThat(InferredPreferenceConfidence.meetsThreshold(confidence, 0.5)).isFalse();
    }
}
