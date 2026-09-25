package com.aifishing.fishingtemplate.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishingTemplateResponse(
        UUID id,
        UUID userId,
        UUID lakeId,
        String name,
        List<FishingTemplateTargetResponse> targets,
        Instant createdAt,
        Instant updatedAt
) {
}
