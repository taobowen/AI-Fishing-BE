package com.aifishing.planning.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpotReasonTest {

    @Test
    void keepsDepthWindowAndConfidenceWhenPresent() {
        assertThat(SpotReason.format(
                "HUMP",
                4.2,
                0.7,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                null
        )).isEqualTo("hump at 4.2m (70% feature confidence) during 08:00–12:00.");
    }

    @Test
    void omitsUnspecifiedDepthAndNullWindows() {
        assertThat(SpotReason.format("DROP_OFF", null, 0.5, null, null, null))
                .isEqualTo("drop-off (50% feature confidence).");
        assertThat(SpotReason.format("DROP_OFF", null, null, null, null, "steep break"))
                .isEqualTo("drop-off. steep break");
    }
}
