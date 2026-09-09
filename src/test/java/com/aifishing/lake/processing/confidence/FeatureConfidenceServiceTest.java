package com.aifishing.lake.processing.confidence;

import com.aifishing.lake.processing.extract.FeatureEvidence;
import com.aifishing.lake.processing.extract.LakeSourceQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureConfidenceServiceTest {

    private final FeatureConfidenceService service = new FeatureConfidenceService();

    @Test
    void bathymetrySupportAndProminenceRaiseConfidence() {
        LakeSourceQuality rich = new LakeSourceQuality(12, 20, true, true, true, true, true, 40.0);
        LakeSourceQuality sparse = new LakeSourceQuality(1, 0, true, false, false, false, false, 200.0);
        FeatureEvidence strong = new FeatureEvidence(0.9, true, true, 10.0, 3);
        FeatureEvidence weak = new FeatureEvidence(0.1, false, false, 300.0, 1);

        BigDecimal high = service.confidence(rich, strong);
        BigDecimal low = service.confidence(sparse, weak);

        assertThat(high).isGreaterThan(low);
        assertThat(high.doubleValue()).isBetween(0.05, 0.99);
        assertThat(low.doubleValue()).isBetween(0.05, 0.99);
    }
}
