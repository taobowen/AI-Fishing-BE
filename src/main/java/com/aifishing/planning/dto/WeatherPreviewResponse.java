package com.aifishing.planning.dto;

public record WeatherPreviewResponse(
        boolean available,
        String periodLabel,
        Double temperatureC,
        Double windSpeedKmh,
        Double windDirectionDeg,
        String condition,
        Integer weatherCode
) {
    public static WeatherPreviewResponse unavailable() {
        return new WeatherPreviewResponse(false, null, null, null, null, null, null);
    }
}
