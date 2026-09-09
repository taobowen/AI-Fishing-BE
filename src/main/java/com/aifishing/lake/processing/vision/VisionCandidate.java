package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Geometry;

public record VisionCandidate(
        FeatureType type,
        Geometry geometry,
        Double modelConfidence,
        String evidence
) {
}
