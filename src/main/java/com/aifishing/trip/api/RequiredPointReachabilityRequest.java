package com.aifishing.trip.api;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.common.jackson.FlexibleLocalTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record RequiredPointReachabilityRequest(
        @NotNull FishingMode fishingMode,
        UUID boatId,
        @Valid GeoPointDto routeStart,
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingStartTime,
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingEndTime
) {
}
