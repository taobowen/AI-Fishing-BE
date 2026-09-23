package com.aifishing.feedback.catchlog.dto;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.feedback.catchlog.domain.SizeBucket;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateCatchRequest(
        @NotBlank String clientCatchId,
        @NotNull Instant occurredAt,
        @Valid GeoPointDto location,
        Double accuracyM,
        UUID waypointId,
        FishSpecies species,
        Double lengthCm,
        Double weightKg,
        TechniqueType techniqueType,
        String lureName,
        String notes,
        UUID fishInteractionId,
        @JsonProperty("isTargetSpecies") Boolean isTargetSpecies,
        SizeBucket sizeBucket
) {
}
