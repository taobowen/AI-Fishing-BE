package com.aifishing.trip.api;

import com.aifishing.common.geo.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredPointRequest(
        @NotNull @Valid GeoPointDto location,
        @Size(max = 128) String label
) {
}
