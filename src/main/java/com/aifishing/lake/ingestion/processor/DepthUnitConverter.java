package com.aifishing.lake.ingestion.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class DepthUnitConverter {

    private static final BigDecimal FEET_TO_METERS = new BigDecimal("0.3048");

    private DepthUnitConverter() {
    }

    public static BigDecimal toMeters(BigDecimal value, String unit) {
        if (value == null) {
            return null;
        }
        if (unit == null || unit.isBlank()) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = unit.toLowerCase(Locale.ROOT);
        if (normalized.contains("ft") || normalized.contains("foot") || normalized.contains("feet")) {
            return value.multiply(FEET_TO_METERS).setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
