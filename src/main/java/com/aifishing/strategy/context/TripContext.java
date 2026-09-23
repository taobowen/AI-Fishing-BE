package com.aifishing.strategy.context;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TripContext(
        UUID tripId,
        LocalDate plannedDate,
        LocalDate plannedEndDate,
        LocalTime fishingStartTime,
        LocalTime fishingEndTime,
        String timeZoneId,
        FishSpecies primaryTargetSpecies,
        List<FishSpecies> secondaryTargetSpecies,
        FishingMode fishingMode,
        UUID boatId
) {
    public TripContext {
        secondaryTargetSpecies = secondaryTargetSpecies == null ? List.of() : List.copyOf(secondaryTargetSpecies);
    }
}
