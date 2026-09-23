package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;

import java.util.UUID;

public record GetHistoricalPerformanceParams(
        UUID lakeId,
        UUID tripWaypointId,
        FishSpecies targetSpecies,
        LureFamily lureFamily
) {
}
