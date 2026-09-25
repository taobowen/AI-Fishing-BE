package com.aifishing.fishingtemplate.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateFishingTemplateRequest(
        @NotNull UUID lakeId,
        @NotBlank @Size(max = 128) String name,
        @Valid List<FishingTemplateTargetRequest> targets
) {
}
