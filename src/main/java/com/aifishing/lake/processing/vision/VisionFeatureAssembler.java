package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.FeatureEvidence;
import com.aifishing.lake.processing.extract.FeatureFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VisionFeatureAssembler {

    public static final String SOURCE_METHOD = "VISION_CANONICAL_RENDER";

    private final FeatureFactory featureFactory;
    private final VisionProperties visionProperties;

    public VisionFeatureAssembler(FeatureFactory featureFactory, VisionProperties visionProperties) {
        this.featureFactory = featureFactory;
        this.visionProperties = visionProperties;
    }

    public LakeFeature toFeature(AnalysisContext context, VisionCandidate candidate) {
        double prominence = candidate.modelConfidence() == null ? 0.55 : clamp(candidate.modelConfidence());
        int supporting = 1;
        if (context.hasBathymetryGeometry()) {
            supporting++;
        }
        if (context.lakeBoundary() != null) {
            supporting++;
        }
        FeatureEvidence evidence = new FeatureEvidence(
                prominence,
                context.hasBathymetryGeometry(),
                candidate.geometry() != null && candidate.geometry().isValid(),
                null,
                supporting
        );
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("promptVersion", visionProperties.getPromptVersion());
        if (candidate.modelConfidence() != null) {
            extra.put("modelConfidence", candidate.modelConfidence());
        }
        if (candidate.evidence() != null) {
            extra.put("visionEvidence", candidate.evidence());
        }
        return featureFactory.create(
                context,
                candidate.type(),
                candidate.geometry(),
                null,
                null,
                null,
                null,
                null,
                SOURCE_METHOD,
                null,
                evidence,
                extra
        );
    }

    private double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(1.0, value);
    }
}
