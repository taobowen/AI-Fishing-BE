package com.aifishing.boat.api;

import com.aifishing.common.enums.WindWaveCapability;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AnalyzeBoatRequest(
        @NotBlank String configurationDescription,
        BigDecimal savedCruiseSpeedKmh,
        BigDecimal savedPracticalRangeKm,
        WindWaveCapability savedWindWaveCapability
) {
}
