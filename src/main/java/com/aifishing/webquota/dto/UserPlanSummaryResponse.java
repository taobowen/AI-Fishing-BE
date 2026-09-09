package com.aifishing.webquota.dto;

import com.aifishing.common.enums.ClientChannel;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UserPlanSummaryResponse(
        UUID tripId,
        UUID planId,
        UUID lakeId,
        String lakeName,
        LocalDate plannedDate,
        FishSpecies primaryTargetSpecies,
        LocalTime fishingStartTime,
        LocalTime fishingEndTime,
        FishingMode fishingMode,
        ClientChannel clientChannel,
        Instant generatedAt,
        TripPlanStatus status
) {
}
