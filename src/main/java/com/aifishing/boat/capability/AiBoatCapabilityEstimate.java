package com.aifishing.boat.capability;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiBoatCapabilityEstimate(
        MetricNumber cruiseSpeedKmh,
        MetricNumber practicalRangeKm,
        MetricEnum windWaveCapability,
        Reasoning reasoningSummary,
        List<String> warnings,
        List<String> extractionGaps,
        List<String> missingHints,
        BoatType extractedType,
        List<PropulsionType> extractedPropulsionTypes,
        List<ExtractedMotor> extractedMotors,
        List<Evidence> evidence,
        Boolean webEvidenceUsed
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetricNumber(Double value, Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetricEnum(WindWaveCapability value, Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reasoning(String speed, String range, String windWave) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(String type, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedMotor(
            PropulsionType propulsionType,
            String manufacturer,
            String model,
            BigDecimal horsepower,
            BigDecimal thrustLb
    ) {
    }
}
