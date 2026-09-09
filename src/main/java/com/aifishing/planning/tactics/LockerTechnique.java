package com.aifishing.planning.tactics;

import com.aifishing.common.enums.PresentationTechnique;

public record LockerTechnique(
        PresentationTechnique presentation,
        String instructions,
        String rationale
) {
}
