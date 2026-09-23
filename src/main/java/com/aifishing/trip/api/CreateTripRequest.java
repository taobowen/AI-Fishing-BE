package com.aifishing.trip.api;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.jackson.FlexibleLocalTimeDeserializer;
import com.aifishing.launch.api.TripLaunchSelectionRequest;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateTripRequest(
        @NotNull UUID lakeId,
        @NotNull FishSpecies primaryTargetSpecies,
        List<FishSpecies> secondaryTargetSpecies,
        @NotNull LocalDate plannedDate,
        LocalDate plannedEndDate,
        @NotNull @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingStartTime,
        @NotNull @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingEndTime,
        UUID boatId,
        @NotNull FishingMode fishingMode,
        TripStatus status,
        String notes,
        @Valid TripLaunchSelectionRequest launchSelection
) {
}
