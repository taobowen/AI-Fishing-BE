package com.aifishing.boat.api;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatProvenance;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BoatResponse(
        UUID id,
        UUID userId,
        String name,
        BoatType type,
        String manufacturer,
        String model,
        Integer year,
        List<PropulsionType> propulsionTypes,
        PropulsionType primaryTransitPropulsionType,
        List<BoatMotor> motors,
        String configurationDescription,
        BigDecimal maxSpeedKmh,
        BigDecimal measuredCruiseSpeedKmh,
        BigDecimal comfortableRoundTripRangeKm,
        WindWaveCapability windWaveOverride,
        String freeTextHash,
        String notes,
        boolean active,
        boolean systemGenerated,
        BoatProvenance provenance,
        Instant createdAt,
        Instant updatedAt,
        ResolvedBoatCapabilityPreview resolvedCapability
) {
    public static BoatResponse from(Boat boat, ResolvedBoatCapabilityPreview preview) {
        return new BoatResponse(
                boat.getId(),
                boat.getUserId(),
                boat.getName(),
                boat.getType(),
                boat.getManufacturer(),
                boat.getModel(),
                boat.getYear(),
                boat.getPropulsionTypes(),
                boat.getPrimaryTransitPropulsionType(),
                boat.getMotors(),
                boat.getConfigurationDescription(),
                boat.getMaxSpeedKmh(),
                boat.getMeasuredCruiseSpeedKmh(),
                boat.getComfortableRoundTripRangeKm(),
                boat.getWindWaveOverride(),
                boat.getFreeTextHash(),
                boat.getNotes(),
                boat.isActive(),
                boat.isSystemGenerated(),
                boat.getProvenance(),
                boat.getCreatedAt(),
                boat.getUpdatedAt(),
                preview
        );
    }
}
