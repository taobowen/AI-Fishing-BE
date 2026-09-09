package com.aifishing.strategy.domain;

public record WeatherInterpretation(
        String summary,
        Double confidence
) {
}
