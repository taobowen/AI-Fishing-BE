package com.aifishing.guidance.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherSnapshotFreshnessTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(15);

    @Test
    void observedAtWithinMaxAgeIsFresh() {
        assertThat(WeatherSnapshotFreshness.isFresh(NOW.minus(Duration.ofMinutes(15)), NOW, MAX_AGE)).isTrue();
        assertThat(WeatherSnapshotFreshness.isFresh(NOW.minus(Duration.ofMinutes(14)), NOW, MAX_AGE)).isTrue();
    }

    @Test
    void olderThanMaxAgeIsStale() {
        assertThat(WeatherSnapshotFreshness.isFresh(NOW.minus(Duration.ofMinutes(16)), NOW, MAX_AGE)).isFalse();
    }

    @Test
    void missingObservedAtIsStale() {
        assertThat(WeatherSnapshotFreshness.isFresh(null, NOW, MAX_AGE)).isFalse();
    }
}
