package com.aifishing.trip.api;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.jackson.FlexibleLocalTimeDeserializer;
import com.aifishing.launch.api.TripLaunchSelectionRequest;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record UpdateTripRequest(
        UUID lakeId,
        FishSpecies primaryTargetSpecies,
        List<FishSpecies> secondaryTargetSpecies,
        LocalDate plannedDate,
        LocalDate plannedEndDate,
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingStartTime,
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingEndTime,
        UUID boatId,
        FishingMode fishingMode,
        PlanningMode planningMode,
        UUID fishingTemplateId,
        @Valid List<RequiredPointRequest> requiredPoints,
        TripStatus status,
        String notes,
        @Valid TripLaunchSelectionRequest launchSelection
) {
}
