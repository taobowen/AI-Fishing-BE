package com.aifishing.launch.api;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.geo.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomLaunchPreviewRequest(
        @NotNull @Valid GeoPointDto requestedPoint,
        UUID boatId,
        CustomAccessType customAccessType
) {
}
