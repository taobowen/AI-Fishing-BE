package com.aifishing.fishingsession;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.session")
public class SessionProperties {

    private Waypoint waypoint = new Waypoint();
    private Location location = new Location();
    private Navigation navigation = new Navigation();

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
}
