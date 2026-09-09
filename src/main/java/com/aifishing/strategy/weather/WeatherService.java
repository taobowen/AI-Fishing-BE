package com.aifishing.strategy.weather;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class WeatherService {

    private final WeatherProvider weatherProvider;

    public WeatherService(WeatherProvider weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    public WeatherContext forTrip(
            double latitude,
            double longitude,
            String timeZoneId,
            LocalDate date,
            LocalTime fishingStart,
            LocalTime fishingEnd
    ) {
        WeatherContext context = weatherProvider.forecast(latitude, longitude, timeZoneId, date, fishingStart, fishingEnd);
        return new WeatherContext(
                context.availability(),
                context.retrievedAt(),
                context.provider(),
                context.timeZoneId(),
                context.forecastDate(),
                false,
                null,
                context.airTemperatureC(),
                context.windSpeedKmh(),
                context.windDirectionDeg(),
                context.precipitationMm(),
                context.cloudCoverPercent(),
                context.pressureHpa(),
                context.sunrise(),
                context.sunset(),
                context.hours(),
                context.notes()
        );
    }
}
