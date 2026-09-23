package com.aifishing.strategy.weather;

import java.time.LocalDate;
import java.time.LocalTime;

public interface WeatherProvider {

    WeatherContext forecast(
            double latitude,
            double longitude,
            String timeZoneId,
            LocalDate date,
            LocalTime fishingStart,
            LocalTime fishingEnd
    );

    default WeatherContext forecast(
            double latitude,
            double longitude,
            String timeZoneId,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime fishingStart,
            LocalTime fishingEnd
    ) {
        return forecast(latitude, longitude, timeZoneId, startDate, fishingStart, fishingEnd);
    }
}
