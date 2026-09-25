package com.aifishing.fishingtemplate.api;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import jakarta.validation.constraints.NotNull;

public record FishingTemplateTargetRequest(
        @NotNull TemplateTargetKind kind,
        String name,
        @NotNull GeoJsonGeometryDto geometry,
        Integer sortOrder
) {
}
