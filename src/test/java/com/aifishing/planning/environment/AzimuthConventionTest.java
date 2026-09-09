package com.aifishing.planning.environment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AzimuthConventionTest {

    @Test
    void wraparound359And1IsTwoDegrees() {
        assertThat(AzimuthConvention.circularDelta(359, 1)).isCloseTo(2.0, within(1e-9));
        assertThat(AzimuthConvention.circularDelta(1, 359)).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void westWindFlowsEast() {
        assertThat(AzimuthConvention.flowFromMeteorological(270)).isCloseTo(90.0, within(1e-9));
        assertThat(AzimuthConvention.normalize(-90)).isCloseTo(270.0, within(1e-9));
        assertThat(AzimuthConvention.normalize(360)).isCloseTo(0.0, within(1e-9));
    }
}
