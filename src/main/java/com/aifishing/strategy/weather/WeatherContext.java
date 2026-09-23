package com.aifishing.strategy.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record WeatherContext(
        WeatherAvailability availability,
        Instant retrievedAt,
        String provider,
        String timeZoneId,
        LocalDate forecastDate,
        boolean waterTemperatureAvailable,
        Double waterTemperatureC,
        Double airTemperatureC,
        Double windSpeedKmh,
        Double windDirectionDeg,
        Double precipitationMm,
        Double cloudCoverPercent,
        Double pressureHpa,
        LocalTime sunrise,
        LocalTime sunset,
        List<HourlyWeather> hours,
        String notes
) {
    public WeatherContext {
        hours = hours == null ? List.of() : List.copyOf(hours);
        waterTemperatureAvailable = false;
        waterTemperatureC = null;
    }

    public record HourlyWeather(
            LocalTime time,
            Double airTemperatureC,
            Double windSpeedKmh,
            Double windDirectionDeg,
            Double precipitationMm,
            Double cloudCoverPercent,
            Double pressureHpa,
            Double shortwaveRadiation,
            Double directRadiation,
            Integer weatherCode,
            LocalDate date
    ) {
        public HourlyWeather(
                LocalTime time,
                Double airTemperatureC,
                Double windSpeedKmh,
                Double windDirectionDeg,
                Double precipitationMm,
                Double cloudCoverPercent,
                Double pressureHpa
        ) {
            this(time, airTemperatureC, windSpeedKmh, windDirectionDeg, precipitationMm, cloudCoverPercent, pressureHpa, null, null, null, null);
        }

        public HourlyWeather(
                LocalTime time,
                Double airTemperatureC,
                Double windSpeedKmh,
                Double windDirectionDeg,
                Double precipitationMm,
                Double cloudCoverPercent,
                Double pressureHpa,
                Double shortwaveRadiation,
                Double directRadiation
        ) {
            this(time, airTemperatureC, windSpeedKmh, windDirectionDeg, precipitationMm, cloudCoverPercent, pressureHpa, shortwaveRadiation, directRadiation, null, null);
        }
    }
}
