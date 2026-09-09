package com.aifishing.strategy.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.weather")
public class WeatherProperties {

    private String provider = "open-meteo";
    private int forecastHorizonDays = 16;
    private int timeoutSeconds = 20;
    private String baseUrl = "https://api.open-meteo.com";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getForecastHorizonDays() {
        return forecastHorizonDays;
    }

    public void setForecastHorizonDays(int forecastHorizonDays) {
        this.forecastHorizonDays = forecastHorizonDays;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
