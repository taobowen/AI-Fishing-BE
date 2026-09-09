package com.aifishing.planning.environment;

/**
 * Single azimuth convention for solar, shoreline, slope, and wind comparisons.
 * 0° = true north, 90° = east, 180° = south, 270° = west, clockwise, range [0, 360).
 */
public final class AzimuthConvention {

    public static final String DESCRIPTION =
            "0°=true north, 90°=east, 180°=south, 270°=west, clockwise, [0, 360)";

    private AzimuthConvention() {
    }

    public static double normalize(double degrees) {
        if (Double.isNaN(degrees) || Double.isInfinite(degrees)) {
            return Double.NaN;
        }
        double x = degrees % 360.0;
        if (x < 0) {
            x += 360.0;
        }
        if (x >= 360.0) {
            x = 0;
        }
        return x;
    }

    /**
     * Smallest absolute circular difference in [0, 180].
     */
    public static double circularDelta(double a, double b) {
        double d = Math.abs(normalize(a) - normalize(b)) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    /**
     * Signed shortest rotation from {@code from} to {@code to} in (-180, 180].
     */
    public static double signedDelta(double from, double to) {
        return (normalize(to) - normalize(from) + 540.0) % 360.0 - 180.0;
    }

    /**
     * Meteorological wind-from azimuth → direction the air moves toward.
     */
    public static double flowFromMeteorological(double windFromAzimuth) {
        return normalize(windFromAzimuth + 180.0);
    }

    public static boolean aligned(double a, double b, double toleranceDeg) {
        return circularDelta(a, b) <= toleranceDeg;
    }
}
