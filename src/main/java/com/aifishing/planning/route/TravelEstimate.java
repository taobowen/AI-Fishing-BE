package com.aifishing.planning.route;

public record TravelEstimate(
        double distanceM,
        double minutes,
        double appliedDetourFactor,
        boolean landCrossingDetected,
        boolean unknownTravel
) {
    public TravelEstimate(double distanceM, double minutes, double appliedDetourFactor, boolean landCrossingDetected) {
        this(distanceM, minutes, appliedDetourFactor, landCrossingDetected, false);
    }

    public static TravelEstimate unspecified() {
        return new TravelEstimate(0, 0, 1, false, true);
    }

    public static TravelEstimate zero() {
        return new TravelEstimate(0, 0, 1, false, false);
    }
}
