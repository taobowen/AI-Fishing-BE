package com.aifishing.lake.processing.extract;

import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;

import java.util.List;

public interface FeatureExtractor {

    FeatureType type();

    List<LakeFeature> extract(AnalysisContext context);
}
