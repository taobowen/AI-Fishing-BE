package com.aifishing.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.feedback")
public class FeedbackProperties {

    private Catch catchConfig = new Catch();
    private Effort effort = new Effort();
    private Performance performance = new Performance();
    private Photo photo = new Photo();

    public Catch getCatch() {
        return catchConfig;
    }

    public void setCatch(Catch catchConfig) {
        this.catchConfig = catchConfig == null ? new Catch() : catchConfig;
    }

    public Effort getEffort() {
        return effort;
    }

    public void setEffort(Effort effort) {
        this.effort = effort == null ? new Effort() : effort;
    }

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance == null ? new Performance() : performance;
    }

    public Photo getPhoto() {
        return photo;
    }

    public void setPhoto(Photo photo) {
        this.photo = photo == null ? new Photo() : photo;
    }

    public static class Catch {
        private double waypointAssociationRadiusM = 150;
        private int maxLocationAgeSeconds = 30;

        public double getWaypointAssociationRadiusM() {
            return waypointAssociationRadiusM;
        }

        public void setWaypointAssociationRadiusM(double waypointAssociationRadiusM) {
            this.waypointAssociationRadiusM = waypointAssociationRadiusM;
        }

        public int getMaxLocationAgeSeconds() {
            return maxLocationAgeSeconds;
        }

        public void setMaxLocationAgeSeconds(int maxLocationAgeSeconds) {
            this.maxLocationAgeSeconds = maxLocationAgeSeconds;
        }
    }

    public static class Effort {
        private String derivationVersion = "effort-v1";
        private double maxTrollingSpeedMps = 4;
        private int maxSampleGapSeconds = 120;

        public String getDerivationVersion() {
            return derivationVersion;
        }

        public void setDerivationVersion(String derivationVersion) {
            this.derivationVersion = derivationVersion;
        }

        public double getMaxTrollingSpeedMps() {
            return maxTrollingSpeedMps;
        }

        public void setMaxTrollingSpeedMps(double maxTrollingSpeedMps) {
            this.maxTrollingSpeedMps = maxTrollingSpeedMps;
        }

        public int getMaxSampleGapSeconds() {
            return maxSampleGapSeconds;
        }

        public void setMaxSampleGapSeconds(int maxSampleGapSeconds) {
            this.maxSampleGapSeconds = maxSampleGapSeconds;
        }
    }

    public static class Performance {
        private int minEffortMinutes = 15;
        private double priorEffortHours = 4;
        private double priorLandedPerHour = 0.5;
        private double historicalRankingWeight = 0.10;
        private double spatialBufferM = 75;
        private double userBlendPriorHours = 4;

        public int getMinEffortMinutes() {
            return minEffortMinutes;
        }

        public void setMinEffortMinutes(int minEffortMinutes) {
            this.minEffortMinutes = minEffortMinutes;
        }

        public double getPriorEffortHours() {
            return priorEffortHours;
        }

        public void setPriorEffortHours(double priorEffortHours) {
            this.priorEffortHours = priorEffortHours;
        }

        public double getPriorLandedPerHour() {
            return priorLandedPerHour;
        }

        public void setPriorLandedPerHour(double priorLandedPerHour) {
            this.priorLandedPerHour = priorLandedPerHour;
        }

        public double getHistoricalRankingWeight() {
            return historicalRankingWeight;
        }

        public void setHistoricalRankingWeight(double historicalRankingWeight) {
            this.historicalRankingWeight = historicalRankingWeight;
        }

        public double getSpatialBufferM() {
            return spatialBufferM;
        }

        public void setSpatialBufferM(double spatialBufferM) {
            this.spatialBufferM = spatialBufferM;
        }

        public double getUserBlendPriorHours() {
            return userBlendPriorHours;
        }

        public void setUserBlendPriorHours(double userBlendPriorHours) {
            this.userBlendPriorHours = userBlendPriorHours;
        }

        public double minEffortHours() {
            return minEffortMinutes / 60.0;
        }
    }

    public static class Photo {
        private long maxBytes = 8_388_608;
        private int presignTtlSeconds = 120;
        private java.util.List<String> allowedContentTypes = java.util.List.of(
                "image/jpeg",
                "image/png",
                "image/webp"
        );

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getPresignTtlSeconds() {
            return presignTtlSeconds;
        }

        public void setPresignTtlSeconds(int presignTtlSeconds) {
            this.presignTtlSeconds = presignTtlSeconds;
        }

        public java.util.List<String> getAllowedContentTypes() {
            return allowedContentTypes;
        }

        public void setAllowedContentTypes(java.util.List<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                    ? java.util.List.of("image/jpeg", "image/png", "image/webp")
                    : List.copyOf(allowedContentTypes);
        }
    }
}
