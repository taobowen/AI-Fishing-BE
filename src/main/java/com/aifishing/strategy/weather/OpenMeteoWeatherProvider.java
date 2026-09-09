package com.aifishing.strategy.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherProvider.class);

    private final WeatherProperties properties;
    private final ObjectMapper objectMapper;

    public OpenMeteoWeatherProvider(WeatherProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeatherContext forecast(
            double latitude,
            double longitude,
            String timeZoneId,
            LocalDate date,
            LocalTime fishingStart,
            LocalTime fishingEnd
    ) {
        Instant retrievedAt = Instant.now();
        ZoneId zone = ZoneId.of(timeZoneId == null || timeZoneId.isBlank() ? "UTC" : timeZoneId);
        LocalDate today = LocalDate.now(zone);
        long daysAhead = ChronoUnit.DAYS.between(today, date);
        if (daysAhead < 0) {
            return empty(WeatherAvailability.UNAVAILABLE, retrievedAt, timeZoneId, date,
                    "Trip date is in the past relative to the lake timezone; forecast product was not queried.");
        }
        if (daysAhead > properties.getForecastHorizonDays()) {
            return empty(WeatherAvailability.OUT_OF_FORECAST_RANGE, retrievedAt, timeZoneId, date,
                    "Trip date is beyond the configured forecast horizon (" + properties.getForecastHorizonDays() + " days).");
        }
        try {
            String uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl() + "/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("hourly", "temperature_2m,precipitation,cloud_cover,wind_speed_10m,wind_direction_10m,surface_pressure,shortwave_radiation,direct_radiation")
                    .queryParam("daily", "sunrise,sunset")
                    .queryParam("timezone", timeZoneId)
                    .queryParam("start_date", date)
                    .queryParam("end_date", date)
                    .queryParam("wind_speed_unit", "kmh")
                    .build(true)
                    .toUriString();
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .build();
            String body = client.get().uri(uri).retrieve().body(String.class);
            return parseForecast(body, retrievedAt, timeZoneId, date, fishingStart, fishingEnd);
        } catch (Exception ex) {
            log.warn("Open-Meteo forecast failed: {}", ex.getMessage());
            return empty(WeatherAvailability.FAILED, retrievedAt, timeZoneId, date,
                    "Weather provider call failed: " + truncate(ex.getMessage()));
        }
    }

    WeatherContext parseForecast(
            String body,
            Instant retrievedAt,
            String timeZoneId,
            LocalDate date,
            LocalTime fishingStart,
            LocalTime fishingEnd
    ) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        List<WeatherContext.HourlyWeather> hours = new ArrayList<>();
        LocalTime start = fishingStart == null ? LocalTime.MIN : fishingStart;
        LocalTime end = fishingEnd == null ? LocalTime.MAX : fishingEnd;
        double airSum = 0;
        double windSum = 0;
        double precipSum = 0;
        double cloudSum = 0;
        double pressureSum = 0;
        int airN = 0;
        int windN = 0;
        int cloudN = 0;
        int pressureN = 0;
        Double windDir = null;
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(times.get(i).asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (!dateTime.toLocalDate().equals(date)) {
                continue;
            }
            LocalTime time = dateTime.toLocalTime();
            if (time.isBefore(start) || time.isAfter(end)) {
                continue;
            }
            Double air = number(hourly.path("temperature_2m"), i);
            Double wind = number(hourly.path("wind_speed_10m"), i);
            Double dir = number(hourly.path("wind_direction_10m"), i);
            Double precip = number(hourly.path("precipitation"), i);
            Double cloud = number(hourly.path("cloud_cover"), i);
            Double pressure = number(hourly.path("surface_pressure"), i);
            Double shortwave = number(hourly.path("shortwave_radiation"), i);
            Double direct = number(hourly.path("direct_radiation"), i);
            hours.add(new WeatherContext.HourlyWeather(time, air, wind, dir, precip, cloud, pressure, shortwave, direct));
            if (air != null) {
                airSum += air;
                airN++;
            }
            if (wind != null) {
                windSum += wind;
                windN++;
            }
            if (precip != null) {
                precipSum += precip;
            }
            if (cloud != null) {
                cloudSum += cloud;
                cloudN++;
            }
            if (pressure != null) {
                pressureSum += pressure;
                pressureN++;
            }
            if (dir != null) {
                windDir = dir;
            }
        }
        JsonNode daily = root.path("daily");
        LocalTime sunrise = dailyTime(daily.path("sunrise"));
        LocalTime sunset = dailyTime(daily.path("sunset"));
        return new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                retrievedAt,
                "open-meteo",
                timeZoneId,
                date,
                false,
                null,
                airN == 0 ? null : airSum / airN,
                windN == 0 ? null : windSum / windN,
                windDir,
                precipSum,
                cloudN == 0 ? null : cloudSum / cloudN,
                pressureN == 0 ? null : pressureSum / pressureN,
                sunrise,
                sunset,
                hours,
                "Air temperature is a forecast of air, not observed lake water temperature."
        );
    }

    private WeatherContext empty(
            WeatherAvailability availability,
            Instant retrievedAt,
            String timeZoneId,
            LocalDate date,
            String notes
    ) {
        return new WeatherContext(
                availability,
                retrievedAt,
                "open-meteo",
                timeZoneId,
                date,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                notes
        );
    }

    private Double number(JsonNode array, int index) {
        if (array == null || !array.isArray() || index >= array.size() || array.get(index).isNull()) {
            return null;
        }
        return array.get(index).asDouble();
    }

    private LocalTime dailyTime(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty() || array.get(0).isNull()) {
            return null;
        }
        String raw = array.get(0).asText();
        if (raw.length() >= 16) {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalTime();
        }
        return LocalTime.parse(raw);
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return factory;
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
