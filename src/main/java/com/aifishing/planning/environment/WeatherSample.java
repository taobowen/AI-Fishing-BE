package com.aifishing.planning.environment;

import java.time.Instant;

public record WeatherSample(
        Instant at,
        Double windSpeedKmh,
        Double windFromAzimuth,
        Double windFlowAzimuth,
        Double cloudCoverPercent,
        Double airTemperatureC,
        Double precipitationMm,
        Double pressureHpa,
        Double shortwaveRadiation,
        Double directRadiation
) {
}
