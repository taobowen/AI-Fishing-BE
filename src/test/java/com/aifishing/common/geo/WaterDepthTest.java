package com.aifishing.common.geo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WaterDepthTest {

    @Test
    void absConvertsOntarioSignedElevationToWaterMeters() {
        assertThat(WaterDepth.meters(-3.7)).isEqualTo(3.7);
        assertThat(WaterDepth.meters(BigDecimal.valueOf(-2.7))).isEqualByComparingTo("2.7");
        assertThat(WaterDepth.meters(3.0)).isEqualTo(3.0);
        assertThat(WaterDepth.meters((Double) null)).isNull();
    }
}
