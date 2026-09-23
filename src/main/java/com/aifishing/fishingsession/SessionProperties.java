package com.aifishing.fishingsession;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.session")
public class SessionProperties {

    private Waypoint waypoint = new Waypoint();
    private Location location = new Location();
    private Navigation navigation = new Navigation();
    private AdHoc adHoc = new AdHoc();
    private Stationary stationary = new Stationary();

    public Waypoint getWaypoint() {
        return waypoint;
    }

    public void setWaypoint(Waypoint waypoint) {
        this.waypoint = waypoint == null ? new Waypoint() : waypoint;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location == null ? new Location() : location;
    }

    public Navigation getNavigation() {
        return navigation;
    }

    public void setNavigation(Navigation navigation) {
        this.navigation = navigation == null ? new Navigation() : navigation;
    }

    public AdHoc getAdHoc() {
        return adHoc;
    }

    public void setAdHoc(AdHoc adHoc) {
        this.adHoc = adHoc == null ? new AdHoc() : adHoc;
    }

    public Stationary getStationary() {
        return stationary;
    }

    public void setStationary(Stationary stationary) {
        this.stationary = stationary == null ? new Stationary() : stationary;
    }

    public static class Waypoint {
        private double approachRadiusM = 150;
        private double arrivalRadiusM = 60;
        private double departureRadiusM = 100;
        private int arrivalConfirmSeconds = 30;
        private int arrivalConfirmSamples = 4;
        private int fishingDwellSeconds = 120;

        public double getApproachRadiusM() {
            return approachRadiusM;
        }

        public void setApproachRadiusM(double approachRadiusM) {
            this.approachRadiusM = approachRadiusM;
        }

        public double getArrivalRadiusM() {
            return arrivalRadiusM;
        }

        public void setArrivalRadiusM(double arrivalRadiusM) {
            this.arrivalRadiusM = arrivalRadiusM;
        }

        public double getDepartureRadiusM() {
            return departureRadiusM;
        }

        public void setDepartureRadiusM(double departureRadiusM) {
            this.departureRadiusM = departureRadiusM;
        }

        public int getArrivalConfirmSeconds() {
            return arrivalConfirmSeconds;
        }

        public void setArrivalConfirmSeconds(int arrivalConfirmSeconds) {
            this.arrivalConfirmSeconds = arrivalConfirmSeconds;
        }

        public int getArrivalConfirmSamples() {
            return arrivalConfirmSamples;
        }

        public void setArrivalConfirmSamples(int arrivalConfirmSamples) {
            this.arrivalConfirmSamples = arrivalConfirmSamples;
        }

        public int getFishingDwellSeconds() {
            return fishingDwellSeconds;
        }

        public void setFishingDwellSeconds(int fishingDwellSeconds) {
            this.fishingDwellSeconds = fishingDwellSeconds;
        }
    }

    public static class Location {
        private double maxAccuracyM = 50;
        private double impossibleSpeedMps = 40;
        private int maxBatchSize = 100;

        public double getMaxAccuracyM() {
            return maxAccuracyM;
        }

        public void setMaxAccuracyM(double maxAccuracyM) {
            this.maxAccuracyM = maxAccuracyM;
        }

        public double getImpossibleSpeedMps() {
            return impossibleSpeedMps;
        }

        public void setImpossibleSpeedMps(double impossibleSpeedMps) {
            this.impossibleSpeedMps = impossibleSpeedMps;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }
    }

    public static class Navigation {
        private double approachRadiusM = 150;
        private double arrivalRadiusM = 60;
        private double offRouteM = 40;

        public double getApproachRadiusM() {
            return approachRadiusM;
        }

        public void setApproachRadiusM(double approachRadiusM) {
            this.approachRadiusM = approachRadiusM;
        }

        public double getArrivalRadiusM() {
            return arrivalRadiusM;
        }

        public void setArrivalRadiusM(double arrivalRadiusM) {
            this.arrivalRadiusM = arrivalRadiusM;
        }

        public double getOffRouteM() {
            return offRouteM;
        }

        public void setOffRouteM(double offRouteM) {
            this.offRouteM = offRouteM;
        }
    }

    public static class AdHoc {
        private int gpsMaxAgeSeconds = 30;
        private int matchRadiusM = 75;
        private double departureRadiusM = 100;
        private int departureConfirmSeconds = 30;
        private int departureConfirmSamples = 4;

        public int getGpsMaxAgeSeconds() {
            return gpsMaxAgeSeconds;
        }

        public void setGpsMaxAgeSeconds(int gpsMaxAgeSeconds) {
            this.gpsMaxAgeSeconds = gpsMaxAgeSeconds;
        }

        public int getMatchRadiusM() {
            return matchRadiusM;
        }

        public void setMatchRadiusM(int matchRadiusM) {
            this.matchRadiusM = matchRadiusM;
        }

        public double getDepartureRadiusM() {
            return departureRadiusM;
        }

        public void setDepartureRadiusM(double departureRadiusM) {
            this.departureRadiusM = departureRadiusM;
        }

        public int getDepartureConfirmSeconds() {
            return departureConfirmSeconds;
        }

        public void setDepartureConfirmSeconds(int departureConfirmSeconds) {
            this.departureConfirmSeconds = departureConfirmSeconds;
        }

        public int getDepartureConfirmSamples() {
            return departureConfirmSamples;
        }

        public void setDepartureConfirmSamples(int departureConfirmSamples) {
            this.departureConfirmSamples = departureConfirmSamples;
        }
    }

    public static class Stationary {
        private double maxSpeedMps = 0.4;
        private double stableRadiusM = 25;
        private int minDurationSeconds = 45;
        private int minSamples = 4;
        private int gpsMaxAgeSeconds = 30;
        private int suppressCooldownSeconds = 300;
        private double suppressMoveM = 40;
        private int recentPointLimit = 32;

        public double getMaxSpeedMps() {
            return maxSpeedMps;
        }

        public void setMaxSpeedMps(double maxSpeedMps) {
            this.maxSpeedMps = maxSpeedMps;
        }

        public double getStableRadiusM() {
            return stableRadiusM;
        }

        public void setStableRadiusM(double stableRadiusM) {
            this.stableRadiusM = stableRadiusM;
        }

        public int getMinDurationSeconds() {
            return minDurationSeconds;
        }

        public void setMinDurationSeconds(int minDurationSeconds) {
            this.minDurationSeconds = minDurationSeconds;
        }

        public int getMinSamples() {
            return minSamples;
        }

        public void setMinSamples(int minSamples) {
            this.minSamples = minSamples;
        }

        public int getGpsMaxAgeSeconds() {
            return gpsMaxAgeSeconds;
        }

        public void setGpsMaxAgeSeconds(int gpsMaxAgeSeconds) {
            this.gpsMaxAgeSeconds = gpsMaxAgeSeconds;
        }

        public int getSuppressCooldownSeconds() {
            return suppressCooldownSeconds;
        }

        public void setSuppressCooldownSeconds(int suppressCooldownSeconds) {
            this.suppressCooldownSeconds = suppressCooldownSeconds;
        }

        public double getSuppressMoveM() {
            return suppressMoveM;
        }

        public void setSuppressMoveM(double suppressMoveM) {
            this.suppressMoveM = suppressMoveM;
        }

        public int getRecentPointLimit() {
            return recentPointLimit;
        }

        public void setRecentPointLimit(int recentPointLimit) {
            this.recentPointLimit = recentPointLimit;
        }
    }
}
