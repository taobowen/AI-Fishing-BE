package com.aifishing.lake.processing.confidence;

import com.aifishing.lake.processing.extract.FeatureEvidence;
import com.aifishing.lake.processing.extract.LakeSourceQuality;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FeatureConfidenceService {

    public BigDecimal confidence(LakeSourceQuality quality, FeatureEvidence evidence) {
        double value = 0.20 * quality.sourceQualityScore()
                + 0.25 * clamp(evidence.prominence())
                + 0.15 * (evidence.bathymetrySupported() ? 1.0 : 0.35)
                + 0.15 * (evidence.geometryConsistent() ? 1.0 : 0.4)
                + 0.10 * interpolationScore(evidence.interpolationDistanceM())
                + 0.15 * Math.min(1.0, evidence.supportingDataTypes() / 3.0);
        return BigDecimal.valueOf(Math.min(0.99, Math.max(0.05, value))).setScale(4, RoundingMode.HALF_UP);
    }

    private double interpolationScore(Double distanceM) {
        if (distanceM == null) {
            return 0.7;
        }
        if (distanceM <= 20) {
            return 1.0;
        }
        if (distanceM >= 250) {
            return 0.25;
        }
        return 1.0 - ((distanceM - 20) / 230.0) * 0.75;
    }

    private double clamp(double prominence) {
        if (prominence <= 0) {
            return 0.2;
        }
        return Math.min(1.0, prominence);
    }
}
