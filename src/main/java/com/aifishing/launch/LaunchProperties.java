package com.aifishing.launch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.launch")
public class LaunchProperties {

    private Custom custom = new Custom();
    private Recommendation recommendation = new Recommendation();

    public Custom getCustom() {
        return custom;
    }

    public void setCustom(Custom custom) {
        this.custom = custom == null ? new Custom() : custom;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation == null ? new Recommendation() : recommendation;
    }

    public static class Custom {
        private double maxSnapMeters = 250;
        private double preferredWaterOffsetMeters = 15;
        private List<Double> fallbackWaterOffsetMeters = new ArrayList<>(List.of(8.0, 3.0));
        private double nearbyOfficialMeters = 100;
        private double materialMoveMeters = 15;
        private String resolutionVersion = "launch-geometry-v1";

        public double getMaxSnapMeters() {
            return maxSnapMeters;
        }

        public void setMaxSnapMeters(double maxSnapMeters) {
            this.maxSnapMeters = maxSnapMeters;
        }

        public double getPreferredWaterOffsetMeters() {
            return preferredWaterOffsetMeters;
        }

        public void setPreferredWaterOffsetMeters(double preferredWaterOffsetMeters) {
            this.preferredWaterOffsetMeters = preferredWaterOffsetMeters;
        }

        public List<Double> getFallbackWaterOffsetMeters() {
            return fallbackWaterOffsetMeters;
        }

        public void setFallbackWaterOffsetMeters(List<Double> fallbackWaterOffsetMeters) {
            this.fallbackWaterOffsetMeters = fallbackWaterOffsetMeters == null
                    ? new ArrayList<>(List.of(8.0, 3.0))
                    : new ArrayList<>(fallbackWaterOffsetMeters);
        }

        public double getNearbyOfficialMeters() {
            return nearbyOfficialMeters;
        }

        public void setNearbyOfficialMeters(double nearbyOfficialMeters) {
            this.nearbyOfficialMeters = nearbyOfficialMeters;
        }

        public double getMaterialMoveMeters() {
            return materialMoveMeters;
        }

        public void setMaterialMoveMeters(double materialMoveMeters) {
            this.materialMoveMeters = materialMoveMeters;
        }

        public String getResolutionVersion() {
            return resolutionVersion;
        }

        public void setResolutionVersion(String resolutionVersion) {
            this.resolutionVersion = resolutionVersion;
        }
    }

    public static class Recommendation {
        private String algorithmVersion = "1.0.0";
        private int topCandidateCount = 8;
        private double coverageWeight = 0.40;
        private double travelWeight = 0.30;
        private double boatFeasibilityWeight = 0.20;
        private double accessConfidenceWeight = 0.10;

        public String getAlgorithmVersion() {
            return algorithmVersion;
        }

        public void setAlgorithmVersion(String algorithmVersion) {
            this.algorithmVersion = algorithmVersion;
        }

        public int getTopCandidateCount() {
            return topCandidateCount;
        }

        public void setTopCandidateCount(int topCandidateCount) {
            this.topCandidateCount = topCandidateCount;
        }

        public double getCoverageWeight() {
            return coverageWeight;
        }

        public void setCoverageWeight(double coverageWeight) {
            this.coverageWeight = coverageWeight;
        }

        public double getTravelWeight() {
            return travelWeight;
        }

        public void setTravelWeight(double travelWeight) {
            this.travelWeight = travelWeight;
        }

        public double getBoatFeasibilityWeight() {
            return boatFeasibilityWeight;
        }

        public void setBoatFeasibilityWeight(double boatFeasibilityWeight) {
            this.boatFeasibilityWeight = boatFeasibilityWeight;
        }

        public double getAccessConfidenceWeight() {
            return accessConfidenceWeight;
        }

        public void setAccessConfidenceWeight(double accessConfidenceWeight) {
            this.accessConfidenceWeight = accessConfidenceWeight;
        }
    }
}
