package com.aifishing.planning.dto;

import com.aifishing.common.jackson.FlexibleLocalTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WeatherPreviewRequest(
        @NotNull UUID lakeId,
        @NotNull LocalDate plannedDate,
        LocalDate plannedEndDate,
        @NotNull @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingStartTime,
        @NotNull @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class) LocalTime fishingEndTime
) {
}
