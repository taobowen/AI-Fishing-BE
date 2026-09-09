package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.LureLengthBand;
import com.aifishing.common.enums.LureWeightBand;
import com.aifishing.common.enums.PresentationTechnique;

import java.util.List;

public record TacticProfile(
        LureFamily lureFamily,
        LureLengthBand lengthBand,
        Double lengthInches,
        LureWeightBand weightBand,
        Double weightOz,
        List<LureColorFamily> preferredColors,
        Double optionalWeight,
        PresentationTechnique presentationTechnique,
        String instructions,
        String rationale,
        String retrieveSpeed,
        String presentationDepth,
        String pathCue
) {
    public TacticProfile {
        preferredColors = preferredColors == null ? List.of() : List.copyOf(preferredColors);
    }

    public boolean requiresLength() {
        return lureFamily != null && lureFamily.requiresLength();
    }

    public boolean requiresWeight() {
        return lureFamily != null && lureFamily.requiresWeight();
    }
}
