package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AttributionWindowsTest {

    private static final Instant START = Instant.parse("2026-09-16T14:00:00Z");

    @Test
    void stayWindowIsMinOfReevaluateAndCap() {
        GuidanceProperties.Attribution cfg = new GuidanceProperties.Attribution();
        cfg.setMaxStayAttributionMinutes(30);
        assertThat(AttributionWindows.windowLength(GuidanceAction.STAY, 45, cfg))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(AttributionWindows.windowLength(GuidanceAction.STAY, 10, cfg))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void moveAndPresentationWindowsMatchFrozenDefaults() {
        GuidanceProperties.Attribution cfg = new GuidanceProperties.Attribution();
        assertThat(AttributionWindows.windowLength(GuidanceAction.MOVE, 99, cfg)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AttributionWindows.windowLength(GuidanceAction.CHANGE_LURE, 99, cfg)).isEqualTo(Duration.ofMinutes(15));
        assertThat(AttributionWindows.windowLength(GuidanceAction.CHANGE_DEPTH, 99, cfg)).isEqualTo(Duration.ofMinutes(15));
        assertThat(AttributionWindows.windowLength(GuidanceAction.CHANGE_RETRIEVE, 99, cfg)).isEqualTo(Duration.ofMinutes(10));
        assertThat(AttributionWindows.windowLength(GuidanceAction.RETURN, 99, cfg)).isEqualTo(Duration.ZERO);
    }

    @Test
    void confidenceDecaysInsideWindowThenHitsZeroAfterDoubleWindow() {
        Duration window = Duration.ofMinutes(10);
        assertThat(AttributionWindows.confidence(START, START, window)).isEqualTo(1.0);
        assertThat(AttributionWindows.confidence(START, START.plus(window), window)).isCloseTo(0.3, Offset.offset(1e-9));
        assertThat(AttributionWindows.confidence(START, START.plus(window.multipliedBy(2)), window)).isZero();
        assertThat(AttributionWindows.dimensionOf(GuidanceAction.MOVE)).contains(AttributionDimension.LOCATION);
        assertThat(AttributionWindows.strategySuccess(OutcomeKind.CATCH_LOST)).isTrue();
        assertThat(AttributionWindows.strategySuccess(OutcomeKind.NO_BITE)).isFalse();
    }
}
