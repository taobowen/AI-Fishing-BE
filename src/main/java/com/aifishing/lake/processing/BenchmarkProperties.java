package com.aifishing.lake.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.benchmark")
public class BenchmarkProperties {

    private String goldResource = "classpath:benchmarks/head-lake-reference.geojson";
    private String screenshotMetricsResource = "classpath:benchmarks/head-direct-screenshot-vision.json";

    public String getGoldResource() {
        return goldResource;
    }

    public void setGoldResource(String goldResource) {
        this.goldResource = goldResource;
    }

    public String getScreenshotMetricsResource() {
        return screenshotMetricsResource;
    }

    public void setScreenshotMetricsResource(String screenshotMetricsResource) {
        this.screenshotMetricsResource = screenshotMetricsResource;
    }
}
