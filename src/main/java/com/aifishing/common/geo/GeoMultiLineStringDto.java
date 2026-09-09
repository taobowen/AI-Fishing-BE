package com.aifishing.common.geo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GeoMultiLineStringDto(@NotEmpty List<@Valid List<GeoPointDto>> lines) {
}
