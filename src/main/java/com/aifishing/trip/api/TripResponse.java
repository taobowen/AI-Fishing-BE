package com.aifishing.trip.api;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.launch.api.TripLaunchSelectionResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID userId,
        UUID lakeId,
        String lakeCardImageUrl,
        String timeZoneId,
        FishSpecies primaryTargetSpecies,
        List<FishSpecies> secondaryTargetSpecies,
        LocalDate plannedDate,
        LocalDate plannedEndDate,
        LocalTime fishingStartTime,
        LocalTime fishingEndTime,
        Instant plannedStartAt,
        Instant plannedEndAt,
        UUID boatId,
        FishingMode fishingMode,
        PlanningMode planningMode,
        UUID fishingTemplateId,
        List<RequiredPointResponse> requiredPoints,
        TripStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        TripLaunchSelectionResponse launchSelection
) {
}
