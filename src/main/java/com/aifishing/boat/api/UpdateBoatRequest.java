package com.aifishing.boat.api;

import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;

import java.math.BigDecimal;
import java.util.List;

public record UpdateBoatRequest(
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
        String notes,
        Boolean active
) {
}
