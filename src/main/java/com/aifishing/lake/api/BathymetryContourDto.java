package com.aifishing.lake.api;

import com.aifishing.common.geo.GeoMultiLineStringDto;

import java.math.BigDecimal;

public record BathymetryContourDto(
        BigDecimal depthM,
        GeoMultiLineStringDto geometry
) {
}
