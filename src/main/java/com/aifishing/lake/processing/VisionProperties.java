package com.aifishing.lake.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vision")
public class VisionProperties {

    private int maxImagePx = 2048;
    private boolean persistMaps = true;
    private double maxMetersPerPixel = 8;
    private double tileOverlapM = 80;
    private double hybridGisMinConfidence = 0.6;
    private String promptVersion = "canonical-render-v1";

    public int getMaxImagePx() {
        return maxImagePx;
    }

    public void setMaxImagePx(int maxImagePx) {
        this.maxImagePx = maxImagePx;
    }

    public boolean isPersistMaps() {
        return persistMaps;
    }

    public void setPersistMaps(boolean persistMaps) {
        this.persistMaps = persistMaps;
    }

    public double getMaxMetersPerPixel() {
        return maxMetersPerPixel;
    }

    public void setMaxMetersPerPixel(double maxMetersPerPixel) {
        this.maxMetersPerPixel = maxMetersPerPixel;
    }

    public double getTileOverlapM() {
        return tileOverlapM;
    }

    public void setTileOverlapM(double tileOverlapM) {
        this.tileOverlapM = tileOverlapM;
    }

    public double getHybridGisMinConfidence() {
        return hybridGisMinConfidence;
    }

    public void setHybridGisMinConfidence(double hybridGisMinConfidence) {
        this.hybridGisMinConfidence = hybridGisMinConfidence;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }
}
