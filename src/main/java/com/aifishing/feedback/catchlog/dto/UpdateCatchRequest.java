package com.aifishing.feedback.catchlog.dto;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.SizeBucket;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateCatchRequest(
        CatchOutcome outcome,
        FishSpecies species,
        Double lengthCm,
        Double weightKg,
        TechniqueType techniqueType,
        String lureName,
        String notes,
        @JsonProperty("isTargetSpecies") Boolean isTargetSpecies,
        SizeBucket sizeBucket
) {
}
