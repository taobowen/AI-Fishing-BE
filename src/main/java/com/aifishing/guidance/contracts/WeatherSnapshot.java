package com.aifishing.guidance.contracts;

import java.time.Instant;

public record WeatherSnapshot(
        String schemaVersion,
        Instant observedAt,
        WeatherCondition weather,
        Double windSpeedKph,
        CompassDirection windDirection,
        Double temperatureC,
        Double pressureHpa
) {
}
