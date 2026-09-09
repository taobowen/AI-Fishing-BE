package com.aifishing.boat.capability;

public final class BoatCapabilityRangeMath {

    private BoatCapabilityRangeMath() {
    }

    public static Double systemUsableRangeKm(Double estimatedPracticalRangeKm, double reserveFraction, boolean reliable) {
        if (!reliable || estimatedPracticalRangeKm == null || estimatedPracticalRangeKm <= 0) {
            return null;
        }
        double reserve = Math.min(0.9, Math.max(0, reserveFraction));
        return estimatedPracticalRangeKm * (1.0 - reserve);
    }

    public static Double effectiveUsableRangeKm(Double systemUsableRangeKm, Double comfortableRoundTripRangeKm) {
        if (systemUsableRangeKm == null) {
            return positive(comfortableRoundTripRangeKm);
        }
        if (comfortableRoundTripRangeKm == null || comfortableRoundTripRangeKm <= 0) {
            return systemUsableRangeKm;
        }
        return Math.min(systemUsableRangeKm, comfortableRoundTripRangeKm);
    }

    public static boolean rangeReliable(Double confidence, double threshold) {
        return confidence != null && confidence >= threshold;
    }

    private static Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }
}
