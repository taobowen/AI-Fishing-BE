package com.aifishing.lake.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.processing")
public class ProcessingProperties {

    private String algorithmVersion = "1.0.0";
    private boolean persistSurfaces = false;
    private String derivedDir = "./data/derived";
    private double closeContourToleranceM = 15;
    private double minClosedAreaM2 = 400;
    private double humpMinReliefM = 0.5;
    private double basinMinReliefM = 0.5;
    private double dropoffMinGradient = 0.04;
    private double dropoffMaxSpacingM = 120;
    private double flatMinAreaM2 = 15000;
    private double flatMaxGradient = 0.012;
    private double pointMinProminenceM = 25;
    private double pointWindowM = 80;
    private double pointMinProminenceRatio = 0.9;
    private double pointMinSpacingM = 60;
    private double islandMinAreaM2 = 150;
    private double islandBathyBufferM = 80;
    private double tileSizeM = 500;

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public boolean isPersistSurfaces() {
        return persistSurfaces;
    }

    public void setPersistSurfaces(boolean persistSurfaces) {
        this.persistSurfaces = persistSurfaces;
    }

    public String getDerivedDir() {
        return derivedDir;
    }

    public void setDerivedDir(String derivedDir) {
        this.derivedDir = derivedDir;
    }

    public double getCloseContourToleranceM() {
        return closeContourToleranceM;
    }

    public void setCloseContourToleranceM(double closeContourToleranceM) {
        this.closeContourToleranceM = closeContourToleranceM;
    }

    public double getMinClosedAreaM2() {
        return minClosedAreaM2;
    }

    public void setMinClosedAreaM2(double minClosedAreaM2) {
        this.minClosedAreaM2 = minClosedAreaM2;
    }

    public double getHumpMinReliefM() {
        return humpMinReliefM;
    }

    public void setHumpMinReliefM(double humpMinReliefM) {
        this.humpMinReliefM = humpMinReliefM;
    }

    public double getBasinMinReliefM() {
        return basinMinReliefM;
    }

    public void setBasinMinReliefM(double basinMinReliefM) {
        this.basinMinReliefM = basinMinReliefM;
    }

    public double getDropoffMinGradient() {
        return dropoffMinGradient;
    }

    public void setDropoffMinGradient(double dropoffMinGradient) {
        this.dropoffMinGradient = dropoffMinGradient;
    }

    public double getDropoffMaxSpacingM() {
        return dropoffMaxSpacingM;
    }

    public void setDropoffMaxSpacingM(double dropoffMaxSpacingM) {
        this.dropoffMaxSpacingM = dropoffMaxSpacingM;
    }

    public double getFlatMinAreaM2() {
        return flatMinAreaM2;
    }

    public void setFlatMinAreaM2(double flatMinAreaM2) {
        this.flatMinAreaM2 = flatMinAreaM2;
    }

    public double getFlatMaxGradient() {
        return flatMaxGradient;
    }

    public void setFlatMaxGradient(double flatMaxGradient) {
        this.flatMaxGradient = flatMaxGradient;
    }

    public double getPointMinProminenceM() {
        return pointMinProminenceM;
    }

    public void setPointMinProminenceM(double pointMinProminenceM) {
        this.pointMinProminenceM = pointMinProminenceM;
    }

    public double getPointWindowM() {
        return pointWindowM;
    }

    public void setPointWindowM(double pointWindowM) {
        this.pointWindowM = pointWindowM;
    }

    public double getPointMinProminenceRatio() {
        return pointMinProminenceRatio;
    }

    public void setPointMinProminenceRatio(double pointMinProminenceRatio) {
        this.pointMinProminenceRatio = pointMinProminenceRatio;
    }

    public double getPointMinSpacingM() {
        return pointMinSpacingM;
    }

    public void setPointMinSpacingM(double pointMinSpacingM) {
        this.pointMinSpacingM = pointMinSpacingM;
    }

    public double getIslandMinAreaM2() {
        return islandMinAreaM2;
    }

    public void setIslandMinAreaM2(double islandMinAreaM2) {
        this.islandMinAreaM2 = islandMinAreaM2;
    }

    public double getIslandBathyBufferM() {
        return islandBathyBufferM;
    }

    public void setIslandBathyBufferM(double islandBathyBufferM) {
        this.islandBathyBufferM = islandBathyBufferM;
    }

    public double getTileSizeM() {
        return tileSizeM;
    }

    public void setTileSizeM(double tileSizeM) {
        this.tileSizeM = tileSizeM;
    }

    public java.util.Map<String, Object> toSnapshot() {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("algorithmVersion", algorithmVersion);
        snapshot.put("persistSurfaces", persistSurfaces);
        snapshot.put("derivedDir", derivedDir);
        snapshot.put("closeContourToleranceM", closeContourToleranceM);
        snapshot.put("minClosedAreaM2", minClosedAreaM2);
        snapshot.put("humpMinReliefM", humpMinReliefM);
        snapshot.put("basinMinReliefM", basinMinReliefM);
        snapshot.put("dropoffMinGradient", dropoffMinGradient);
        snapshot.put("dropoffMaxSpacingM", dropoffMaxSpacingM);
        snapshot.put("flatMinAreaM2", flatMinAreaM2);
        snapshot.put("flatMaxGradient", flatMaxGradient);
        snapshot.put("pointMinProminenceM", pointMinProminenceM);
        snapshot.put("pointWindowM", pointWindowM);
        snapshot.put("pointMinProminenceRatio", pointMinProminenceRatio);
        snapshot.put("pointMinSpacingM", pointMinSpacingM);
        snapshot.put("islandMinAreaM2", islandMinAreaM2);
        snapshot.put("islandBathyBufferM", islandBathyBufferM);
        snapshot.put("tileSizeM", tileSizeM);
        return snapshot;
    }
}
