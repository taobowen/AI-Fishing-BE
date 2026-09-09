package com.aifishing.common.geo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GeoPolygonDto(
        @NotEmpty List<@Valid GeoPointDto> ring,
        List<@NotEmpty List<@Valid GeoPointDto>> holes
) {
    public GeoPolygonDto {
        if (holes == null) {
            holes = List.of();
        }
    }

    public GeoPolygonDto(List<GeoPointDto> ring) {
        this(ring, List.of());
    }
}
