package com.aifishing.lake.processing.benchmark;

import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Geometry;

public record GoldFeature(
        FeatureType type,
        Geometry geometry,
        String name,
        String notes
) {
}
