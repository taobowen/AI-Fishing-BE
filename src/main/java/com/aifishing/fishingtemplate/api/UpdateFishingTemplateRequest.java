package com.aifishing.fishingtemplate.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateFishingTemplateRequest(
        @Size(max = 128) String name,
        @Valid List<FishingTemplateTargetRequest> targets
) {
}
