package com.aifishing.lake.ingestion.processor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DepthUnitConverterTest {

    @Test
    void convertsFeetToMeters() {
        assertThat(DepthUnitConverter.toMeters(new BigDecimal("32.8"), "ft"))
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(DepthUnitConverter.toMeters(new BigDecimal("10"), "m"))
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(DepthUnitConverter.toMeters(new BigDecimal("5"), null))
                .isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
