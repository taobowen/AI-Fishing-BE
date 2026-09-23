package com.aifishing.boat.api;

import com.aifishing.common.enums.WindWaveCapability;

public record BoatCapabilityProposal(
        Double cruiseSpeedKmh,
        Double practicalRangeKm,
        WindWaveCapability windWaveCapability
) {
}
