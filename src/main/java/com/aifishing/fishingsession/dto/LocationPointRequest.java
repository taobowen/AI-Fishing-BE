package com.aifishing.fishingsession.dto;

import com.aifishing.common.geo.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LocationPointRequest(
        @NotBlank String clientPointId,
        @NotNull Instant recordedAt,
        @NotNull @Valid GeoPointDto location,
        Double accuracyM,
        Double altitudeM,
        Double speedMps,
        Double headingDegrees
) {
}
