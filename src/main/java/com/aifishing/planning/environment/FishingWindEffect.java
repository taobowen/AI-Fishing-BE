package com.aifishing.planning.environment;

public final class FishingWindEffect {

    private FishingWindEffect() {
    }

    public static WindOrientation classify(WeatherSample weather, LocalOrientation orientation) {
        if (weather == null || weather.windFromAzimuth() == null) {
            return WindOrientation.UNKNOWN;
        }
        Double facing = orientation == null ? null : orientation.facingAspect();
        if (facing == null || orientation.confidence() == OrientationConfidence.UNKNOWN) {
            return WindOrientation.UNKNOWN;
        }
        double from = weather.windFromAzimuth();
        double delta = AzimuthConvention.circularDelta(from, facing);
        if (delta <= 45) {
            return WindOrientation.WINDWARD;
        }
        if (delta >= 135) {
            return WindOrientation.LEEWARD;
        }
        return WindOrientation.CROSSWIND;
    }

    /**
     * Same fishing-environment effect for every boat type. Bounded by config max-weight.
     */
    public static double effect(WindOrientation orientation, double maxWeight) {
        double signed = switch (orientation) {
            case LEEWARD, PROTECTED -> 0.6;
            case CROSSWIND -> 0.15;
            case WINDWARD, EXPOSED -> -0.35;
            case UNKNOWN -> 0;
        };
        return signed * maxWeight;
    }
}
