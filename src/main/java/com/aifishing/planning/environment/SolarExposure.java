package com.aifishing.planning.environment;

import com.aifishing.strategy.domain.LightPreference;

public final class SolarExposure {

    private SolarExposure() {
    }

    /**
     * Objective sun-on-face exposure in [0, 1]. Never a true-shadow claim.
     */
    public static double orientationExposure(SolarPosition sun, LocalOrientation orientation, SolarInfluence influence) {
        if (sun == null || !sun.sunUp() || influence == null || influence.strength() <= 0) {
            return 0;
        }
        Double facing = orientation == null ? null : orientation.facingAspect();
        if (facing == null || orientation.confidence() == OrientationConfidence.UNKNOWN) {
            return 0.5 * influence.strength();
        }
        double alignment = 1.0 - (AzimuthConvention.circularDelta(sun.azimuthDeg(), facing) / 180.0);
        double confidenceScale = switch (orientation.confidence()) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.7;
            case LOW -> 0.4;
            case UNKNOWN -> 0;
        };
        return clamp(alignment * influence.strength() * confidenceScale);
    }

    public static double fishingEffect(
            LightPreference preference,
            double orientationExposure,
            SolarInfluence influence,
            double maxWeight
    ) {
        LightPreference pref = preference == null ? LightPreference.NEUTRAL : preference;
        if (pref == LightPreference.NEUTRAL || influence == null || influence.strength() <= 0) {
            return 0;
        }
        double signed = switch (pref) {
            case SUN_EXPOSED -> (orientationExposure - 0.5) * 2.0;
            case SHADE_PREFERRED -> (0.5 - orientationExposure) * 2.0;
            case TRANSITION_PREFERRED -> 1.0 - Math.abs(orientationExposure - 0.5) * 2.0;
            case NEUTRAL -> 0;
        };
        return clampSigned(signed) * maxWeight * influence.strength();
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    private static double clampSigned(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(-1, Math.min(1, value));
    }
}
