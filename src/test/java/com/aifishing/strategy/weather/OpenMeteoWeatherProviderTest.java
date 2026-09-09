package com.aifishing.strategy.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoWeatherProviderTest {

    private final WeatherProperties properties = new WeatherProperties();
    private final OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(properties, new ObjectMapper());

    @Test
    void pastDateIsUnavailableAndSetsRetrievedAt() {
        LocalDate past = LocalDate.now(ZoneId.of("America/Toronto")).minusDays(1);
        WeatherContext context = provider.forecast(44.75, -78.92, "America/Toronto", past, LocalTime.of(6, 0), LocalTime.of(15, 0));
        assertThat(context.availability()).isEqualTo(WeatherAvailability.UNAVAILABLE);
        assertThat(context.retrievedAt()).isNotNull();
        assertThat(context.waterTemperatureAvailable()).isFalse();
        assertThat(context.waterTemperatureC()).isNull();
    }

    @Test
    void beyondHorizonIsOutOfRangeWithoutFabricatingTemps() {
        LocalDate far = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(properties.getForecastHorizonDays() + 2);
        WeatherContext context = provider.forecast(44.75, -78.92, "America/Toronto", far, LocalTime.of(6, 0), LocalTime.of(15, 0));
        assertThat(context.availability()).isEqualTo(WeatherAvailability.OUT_OF_FORECAST_RANGE);
        assertThat(context.retrievedAt()).isNotNull();
        assertThat(context.airTemperatureC()).isNull();
        assertThat(context.hours()).isEmpty();
        assertThat(context.notes()).contains("horizon");
    }

    @Test
    void parseNormalizesHourlyWindowsAndLeavesWaterTempUnavailable() throws Exception {
        String body = """
                {
                  "hourly": {
                    "time": ["2026-09-12T05:00", "2026-09-12T07:00", "2026-09-12T16:00"],
                    "temperature_2m": [8.0, 12.0, 18.0],
                    "wind_speed_10m": [5.0, 10.0, 20.0],
                    "wind_direction_10m": [180, 270, 90],
                    "precipitation": [0.0, 0.2, 1.0],
                    "cloud_cover": [90, 70, 40],
                    "surface_pressure": [1010, 1012, 1015]
                  },
                  "daily": {
                    "sunrise": ["2026-09-12T06:42"],
                    "sunset": ["2026-09-12T19:31"]
                  }
                }
                """;
        WeatherContext context = provider.parseForecast(
                body,
                Instant.parse("2026-09-02T12:00:00Z"),
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                LocalTime.of(6, 0),
                LocalTime.of(15, 0)
        );
        assertThat(context.availability()).isEqualTo(WeatherAvailability.FORECAST_AVAILABLE);
        assertThat(context.retrievedAt()).isEqualTo(Instant.parse("2026-09-02T12:00:00Z"));
        assertThat(context.hours()).hasSize(1);
        assertThat(context.hours().get(0).airTemperatureC()).isEqualTo(12.0);
        assertThat(context.hours().get(0).shortwaveRadiation()).isNull();
        assertThat(context.airTemperatureC()).isEqualTo(12.0);
        assertThat(context.sunrise()).isEqualTo(LocalTime.of(6, 42));
        assertThat(context.waterTemperatureAvailable()).isFalse();
        assertThat(context.waterTemperatureC()).isNull();
        assertThat(context.notes()).containsIgnoringCase("air");
    }

    @Test
    void parseReadsRadiationWhenPresent() throws Exception {
        String body = """
                {
                  "hourly": {
                    "time": ["2026-09-12T07:00"],
                    "temperature_2m": [12.0],
                    "wind_speed_10m": [10.0],
                    "wind_direction_10m": [270],
                    "precipitation": [0.0],
                    "cloud_cover": [20],
                    "surface_pressure": [1012],
                    "shortwave_radiation": [410],
                    "direct_radiation": [280]
                  },
                  "daily": {
                    "sunrise": ["2026-09-12T06:42"],
                    "sunset": ["2026-09-12T19:31"]
                  }
                }
                """;
        WeatherContext context = provider.parseForecast(
                body,
                Instant.parse("2026-09-02T12:00:00Z"),
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                LocalTime.of(6, 0),
                LocalTime.of(15, 0)
        );
        assertThat(context.hours()).hasSize(1);
        assertThat(context.hours().get(0).shortwaveRadiation()).isEqualTo(410.0);
        assertThat(context.hours().get(0).directRadiation()).isEqualTo(280.0);
    }
}
