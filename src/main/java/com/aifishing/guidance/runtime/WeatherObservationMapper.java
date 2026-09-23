package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;

import java.time.Instant;

final class WeatherObservationMapper {

    private static final double RAIN_MM = 0.2;
    private static final double CLOUDY_PERCENT = 70.0;

    private WeatherObservationMapper() {
    }

    static WeatherSnapshot fromContext(WeatherContext context, Instant fallbackObservedAt) {
        Instant observedAt = context == null || context.retrievedAt() == null
                ? fallbackObservedAt
                : context.retrievedAt();
        if (context == null || context.availability() != WeatherAvailability.FORECAST_AVAILABLE) {
            return new WeatherSnapshot(
                    GuidanceSchemaVersion.VALUE,
                    observedAt,
                    WeatherCondition.UNKNOWN,
                    context == null ? null : context.windSpeedKmh(),
                    fromDegrees(context == null ? null : context.windDirectionDeg()),
                    context == null ? null : context.airTemperatureC(),
                    context == null ? null : context.pressureHpa()
            );
        }
        return new WeatherSnapshot(
                GuidanceSchemaVersion.VALUE,
                observedAt,
                condition(context),
                context.windSpeedKmh(),
                fromDegrees(context.windDirectionDeg()),
                context.airTemperatureC(),
                context.pressureHpa()
        );
    }

    static WeatherCondition condition(WeatherContext context) {
        if (context == null || context.availability() != WeatherAvailability.FORECAST_AVAILABLE) {
            return WeatherCondition.UNKNOWN;
        }
        Double precip = context.precipitationMm();
        if (precip != null && precip >= RAIN_MM) {
            return WeatherCondition.RAIN;
        }
        Double cloud = context.cloudCoverPercent();
        if (cloud != null && cloud >= CLOUDY_PERCENT) {
            return WeatherCondition.CLOUDY;
        }
        if (cloud != null) {
            return WeatherCondition.CLEAR;
        }
        return WeatherCondition.UNKNOWN;
    }

    static CompassDirection fromDegrees(Double degrees) {
        if (degrees == null || degrees.isNaN()) {
            return null;
        }
        double heading = degrees % 360.0;
        if (heading < 0) {
            heading += 360.0;
        }
        if (heading < 22.5 || heading >= 337.5) {
            return CompassDirection.N;
        }
        if (heading < 67.5) {
            return CompassDirection.NE;
        }
        if (heading < 112.5) {
            return CompassDirection.E;
        }
        if (heading < 157.5) {
            return CompassDirection.SE;
        }
        if (heading < 202.5) {
            return CompassDirection.S;
        }
        if (heading < 247.5) {
            return CompassDirection.SW;
        }
        if (heading < 292.5) {
            return CompassDirection.W;
        }
        return CompassDirection.NW;
    }
}
