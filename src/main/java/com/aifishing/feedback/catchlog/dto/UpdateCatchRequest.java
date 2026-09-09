package com.aifishing.feedback.catchlog.dto;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;

public record UpdateCatchRequest(
        CatchOutcome outcome,
        FishSpecies species,
        Double lengthCm,
        Double weightKg,
        TechniqueType techniqueType,
        String lureName,
        String notes
) {
}
