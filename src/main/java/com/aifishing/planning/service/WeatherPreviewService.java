package com.aifishing.planning.service;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.dto.WeatherPreviewRequest;
import com.aifishing.planning.dto.WeatherPreviewResponse;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherService;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class WeatherPreviewService {

    private static final Logger log = LoggerFactory.getLogger(WeatherPreviewService.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final LakeRepository lakeRepository;
    private final WeatherService weatherService;

    public WeatherPreviewService(LakeRepository lakeRepository, WeatherService weatherService) {
        this.lakeRepository = lakeRepository;
        this.weatherService = weatherService;
    }

    @Transactional(readOnly = true)
    public WeatherPreviewResponse preview(WeatherPreviewRequest request) {
        Lake lake = lakeRepository.findById(request.lakeId())
                .orElseThrow(() -> new BadRequestException("Lake not found"));
        ZoneId zone;
        try {
            zone = ZoneId.of(lake.getTimeZoneId());
        } catch (DateTimeException ex) {
            throw new BadRequestException("Lake has an invalid time zone");
        }
        TripClock.Window window = TripClock.resolve(
                request.plannedDate(),
                request.plannedEndDate(),
                request.fishingStartTime(),
                request.fishingEndTime(),
                zone
        );
        if (!window.endAfterStart()) {
            throw new BadRequestException("Resolved fishing end must be after fishing start in the lake local timezone");
        }
        if (!window.withinMaxDuration()) {
            throw new BadRequestException("Trip duration must be at most 24 hours");
        }
        try {
            Point centroid = lake.getCentroid();
            WeatherContext context = weatherService.forTrip(
                    centroid.getY(),
                    centroid.getX(),
                    lake.getTimeZoneId(),
                    window.plannedDate(),
                    window.plannedEndDate(),
                    window.startTime(),
                    window.endTime()
            );
            if (context == null || context.availability() != WeatherAvailability.FORECAST_AVAILABLE) {
                return WeatherPreviewResponse.unavailable();
            }
            WeatherContext.HourlyWeather hour = nearestHour(context, window.startTime());
            Double temperature = hour == null ? context.airTemperatureC() : hour.airTemperatureC();
            Double wind = hour == null ? context.windSpeedKmh() : hour.windSpeedKmh();
            Double direction = hour == null ? context.windDirectionDeg() : hour.windDirectionDeg();
            Integer code = hour == null ? null : hour.weatherCode();
            if (temperature == null && wind == null && context.hours().isEmpty()) {
                return WeatherPreviewResponse.unavailable();
            }
            LocalTime labelTime = hour == null || hour.time() == null ? window.startTime() : hour.time();
            return new WeatherPreviewResponse(
                    true,
                    labelTime == null ? null : HOUR.format(labelTime),
                    temperature,
                    wind,
                    direction,
                    condition(code, hour == null ? context.precipitationMm() : hour.precipitationMm(),
                            hour == null ? context.cloudCoverPercent() : hour.cloudCoverPercent()),
                    code
            );
        } catch (RuntimeException ex) {
            log.warn("Weather preview failed: {}", ex.getMessage());
            return WeatherPreviewResponse.unavailable();
        }
    }

    private static WeatherContext.HourlyWeather nearestHour(WeatherContext context, LocalTime start) {
        if (context.hours().isEmpty()) {
            return null;
        }
        WeatherContext.HourlyWeather best = context.hours().getFirst();
        if (start == null || best.time() == null) {
            return best;
        }
        int bestDelta = Math.abs(best.time().toSecondOfDay() - start.toSecondOfDay());
        for (WeatherContext.HourlyWeather hour : context.hours()) {
            if (hour.time() == null) {
                continue;
            }
            int delta = Math.abs(hour.time().toSecondOfDay() - start.toSecondOfDay());
            if (delta < bestDelta) {
                best = hour;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static String condition(Integer weatherCode, Double precipitationMm, Double cloudCoverPercent) {
        if (weatherCode != null) {
            if (weatherCode == 0) {
                return "CLEAR";
            }
            if (weatherCode <= 3) {
                return "PARTLY_CLOUDY";
            }
            if (weatherCode <= 49) {
                return "FOG";
            }
            if (weatherCode <= 59) {
                return "DRIZZLE";
            }
            if (weatherCode <= 69) {
                return "RAIN";
            }
            if (weatherCode <= 79) {
                return "SNOW";
            }
            if (weatherCode <= 84) {
                return "SHOWERS";
            }
            if (weatherCode <= 94) {
                return "SNOW_SHOWERS";
            }
            return "THUNDERSTORM";
        }
        if (precipitationMm != null && precipitationMm >= 0.2) {
            return "RAIN";
        }
        if (cloudCoverPercent != null && cloudCoverPercent >= 70) {
            return "CLOUDY";
        }
        if (cloudCoverPercent != null && cloudCoverPercent >= 30) {
            return "PARTLY_CLOUDY";
        }
        return "CLEAR";
    }
}
