package com.aifishing.strategy.domain;

import com.aifishing.lake.processing.dto.FeatureType;

public record StructurePreference(
        FeatureType type,
        double weight,
        String rationale
) {
}
