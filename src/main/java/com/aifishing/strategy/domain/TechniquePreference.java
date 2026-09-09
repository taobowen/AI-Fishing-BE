package com.aifishing.strategy.domain;

import com.aifishing.common.enums.TechniqueType;

public record TechniquePreference(
        TechniqueType type,
        double weight,
        String rationale
) {
}
