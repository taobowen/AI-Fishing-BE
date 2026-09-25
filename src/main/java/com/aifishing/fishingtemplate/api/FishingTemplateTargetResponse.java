package com.aifishing.fishingtemplate.api;

import com.aifishing.common.geo.GeoJsonGeometryDto;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;

import java.util.UUID;

public record FishingTemplateTargetResponse(
        UUID id,
        TemplateTargetKind kind,
        String name,
        GeoJsonGeometryDto geometry,
        int sortOrder
) {
}
