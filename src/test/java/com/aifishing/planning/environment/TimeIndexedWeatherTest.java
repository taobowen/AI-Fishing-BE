package com.aifishing.planning.environment;

import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimeIndexedWeatherTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Test
    void interpolatesHourlyWindAndRadiationAtInstant() {
        WeatherContext snapshot = snapshot(List.of(
                hour(LocalTime.of(8, 0), 10, 270, 20, 100),
                hour(LocalTime.of(9, 0), 20, 280, 40, 400)
        ));
        TimeIndexedWeather indexed = TimeIndexedWeather.from(snapshot, TORONTO);
        Instant mid = ZonedDateTime.of(2026, 9, 12, 8, 30, 0, 0, TORONTO).toInstant();
        WeatherSample sample = indexed.at(mid);
        assertThat(sample.windSpeedKmh()).isCloseTo(15.0, within(0.01));
        assertThat(sample.directRadiation()).isCloseTo(250.0, within(0.01));
        assertThat(sample.windFromAzimuth()).isCloseTo(275.0, within(0.5));
        assertThat(sample.windFlowAzimuth()).isCloseTo(AzimuthConvention.flowFromMeteorological(275), within(0.5));
    }

    @Test
    void intervalMaximumWindUsesStormyTransit() {
        WeatherContext snapshot = snapshot(List.of(
                hour(LocalTime.of(8, 0), 42, 270, 90, 50),
                hour(LocalTime.of(9, 0), 8, 270, 20, 600)
        ));
        TimeIndexedWeather indexed = TimeIndexedWeather.from(snapshot, TORONTO);
        Instant depart = ZonedDateTime.of(2026, 9, 12, 8, 0, 0, 0, TORONTO).toInstant();
        Instant arrive = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, TORONTO).toInstant();
        WeatherSample max = indexed.intervalMaximumWind(depart, arrive);
        assertThat(max.windSpeedKmh()).isGreaterThanOrEqualTo(42);
        WeatherSample dest = indexed.at(arrive);
        assertThat(dest.windSpeedKmh()).isCloseTo(8.0, within(0.01));
    }

    @Test
    void missingRadiationFallsBackToAggregatesWithoutFabricating() {
        WeatherContext snapshot = new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                14.0,
                10.0,
                240.0,
                0.1,
                70.0,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                List.of(),
                "air"
        );
        WeatherSample sample = TimeIndexedWeather.from(snapshot, TORONTO)
                .at(ZonedDateTime.of(2026, 9, 12, 10, 0, 0, 0, TORONTO).toInstant());
        assertThat(sample.windSpeedKmh()).isEqualTo(10.0);
        assertThat(sample.directRadiation()).isNull();
        assertThat(sample.shortwaveRadiation()).isNull();
    }

    private static WeatherContext snapshot(List<WeatherContext.HourlyWeather> hours) {
        return new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                14.0,
                10.0,
                270.0,
                0.0,
                20.0,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                hours,
                "air"
        );
    }

    private static WeatherContext.HourlyWeather hour(
            LocalTime time,
            double wind,
            double from,
            double cloud,
            double radiation
    ) {
        return new WeatherContext.HourlyWeather(time, 16.0, wind, from, 0.0, cloud, 1013.0, radiation, radiation);
    }
}
