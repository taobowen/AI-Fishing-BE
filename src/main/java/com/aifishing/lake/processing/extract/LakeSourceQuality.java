package com.aifishing.lake.processing.extract;

public record LakeSourceQuality(
        int contourCount,
        int bathymetryPointCount,
        boolean hasBathymetryIndex,
        boolean hasBathymetryLines,
        boolean hasBathymetryPoints,
        boolean hasShoreline,
        boolean hasIslands,
        Double meanContourSpacingM
) {
    public double sourceQualityScore() {
        double score = 0.35;
        if (hasBathymetryLines) {
            score += 0.25;
        }
        if (hasBathymetryPoints) {
            score += 0.15;
        } else if (hasBathymetryIndex) {
            score += 0.05;
        }
        if (contourCount >= 8) {
            score += 0.1;
        } else if (contourCount >= 3) {
            score += 0.05;
        }
        if (hasShoreline) {
            score += 0.08;
        }
        if (hasIslands) {
            score += 0.02;
        }
        if (meanContourSpacingM != null && meanContourSpacingM < 80) {
            score += 0.05;
        }
        return Math.min(1.0, score);
    }
}
