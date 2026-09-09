package com.aifishing.planning.environment;

public record LocalOrientation(
        Double shorelineWaterFacingAspect,
        Double slopeAspect,
        Double rawFeatureOrientation,
        boolean rawOrientationUsedAsFacing,
        OrientationConfidence confidence,
        String source
) {
    public static LocalOrientation unknown() {
        return new LocalOrientation(null, null, null, false, OrientationConfidence.UNKNOWN, "UNKNOWN");
    }

    public Double facingAspect() {
        if (shorelineWaterFacingAspect != null) {
            return shorelineWaterFacingAspect;
        }
        return slopeAspect;
    }
}
