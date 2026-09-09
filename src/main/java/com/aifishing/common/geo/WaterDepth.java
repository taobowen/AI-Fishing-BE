package com.aifishing.common.geo;

import java.math.BigDecimal;

/**
 * Fishing depth in meters of water. Ontario bathymetry often stores signed
 * elevation (negative = below datum); strategy windows use positive water depth.
 */
public final class WaterDepth {

    private WaterDepth() {
    }

    public static Double meters(Double signed) {
        return signed == null ? null : Math.abs(signed);
    }

    public static BigDecimal meters(BigDecimal signed) {
        return signed == null ? null : signed.abs();
    }
}
