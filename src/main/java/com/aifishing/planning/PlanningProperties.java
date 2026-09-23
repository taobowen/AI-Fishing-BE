package com.aifishing.planning;

import com.aifishing.common.enums.BoatType;
import com.aifishing.lake.processing.dto.Pipeline;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.planning")
public class PlanningProperties {

    private String algorithmVersion = "1.5.0";
    private Pipeline userDefaultFeaturePipeline = Pipeline.GIS;
    private Candidates candidates = new Candidates();
    private Ranking ranking = new Ranking();
    private Schedule schedule = new Schedule();
    private Travel travel = new Travel();
    private Access access = new Access();
    private BoatLimits boat = new BoatLimits();
    private Safety safety = new Safety();
    private Environment environment = new Environment();
    private Spatial spatial = new Spatial();
    private Search search = new Search();
    private Tactics tactics = new Tactics();

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public Pipeline getUserDefaultFeaturePipeline() {
        return userDefaultFeaturePipeline == null ? Pipeline.GIS : userDefaultFeaturePipeline;
    }

    public void setUserDefaultFeaturePipeline(Pipeline userDefaultFeaturePipeline) {
        this.userDefaultFeaturePipeline = userDefaultFeaturePipeline;
    }

    public Candidates getCandidates() {
        return candidates;
    }

    public void setCandidates(Candidates candidates) {
        this.candidates = candidates;
    }

    public Ranking getRanking() {
        return ranking;
    }

    public void setRanking(Ranking ranking) {
        this.ranking = ranking;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public Travel getTravel() {
        return travel;
    }

    public void setTravel(Travel travel) {
        this.travel = travel;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    public BoatLimits getBoat() {
        return boat;
    }

    public void setBoat(BoatLimits boat) {
        this.boat = boat == null ? new BoatLimits() : boat;
    }

    public Safety getSafety() {
        return safety;
    }

    public void setSafety(Safety safety) {
        this.safety = safety;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment == null ? new Environment() : environment;
    }

    public Spatial getSpatial() {
        return spatial;
    }

    public void setSpatial(Spatial spatial) {
        this.spatial = spatial == null ? new Spatial() : spatial;
    }

    public Search getSearch() {
        return search;
    }

    public void setSearch(Search search) {
        this.search = search == null ? new Search() : search;
    }

    public Tactics getTactics() {
        return tactics;
    }

    public void setTactics(Tactics tactics) {
        this.tactics = tactics == null ? new Tactics() : tactics;
    }

    public double maxOneWayKm(BoatType type) {
        return boat.maxOneWayKm(type);
    }

    public static class Candidates {
        private double minSpacingM = 150;
        private int maxPerFeatureType = 10;
        private int maxTotal = 32;
        private double depthToleranceM = 0;
        private double fallbackDepthToleranceM = 1.5;
        private double minStrategyWeight = 0.05;
        private int maxMacroZones = 8;
        private int maxUnassignedAtomics = 8;
        private int maxMacroVisitOptions = 48;
        private int maxPerRegionFeatureType = 3;
        private double regionCellSizeM = 2500;

        public double getMinSpacingM() {
            return minSpacingM;
        }

        public void setMinSpacingM(double minSpacingM) {
            this.minSpacingM = minSpacingM;
        }

        public int getMaxPerFeatureType() {
            return maxPerFeatureType;
        }

        public void setMaxPerFeatureType(int maxPerFeatureType) {
            this.maxPerFeatureType = maxPerFeatureType;
        }

        public int getMaxTotal() {
            return maxTotal;
        }

        public void setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
        }

        public double getDepthToleranceM() {
            return depthToleranceM;
        }

        public void setDepthToleranceM(double depthToleranceM) {
            this.depthToleranceM = depthToleranceM;
        }

        public double getFallbackDepthToleranceM() {
            return fallbackDepthToleranceM;
        }

        public void setFallbackDepthToleranceM(double fallbackDepthToleranceM) {
            this.fallbackDepthToleranceM = fallbackDepthToleranceM;
        }

        public double getMinStrategyWeight() {
            return minStrategyWeight;
        }

        public void setMinStrategyWeight(double minStrategyWeight) {
            this.minStrategyWeight = minStrategyWeight;
        }

        public int getMaxMacroZones() {
            return maxMacroZones <= 0 ? 8 : maxMacroZones;
        }

        public void setMaxMacroZones(int maxMacroZones) {
            this.maxMacroZones = maxMacroZones;
        }

        public int getMaxUnassignedAtomics() {
            return maxUnassignedAtomics <= 0 ? 8 : maxUnassignedAtomics;
        }

        public void setMaxUnassignedAtomics(int maxUnassignedAtomics) {
            this.maxUnassignedAtomics = maxUnassignedAtomics;
        }

        public int getMaxMacroVisitOptions() {
            return maxMacroVisitOptions <= 0 ? 48 : maxMacroVisitOptions;
        }

        public void setMaxMacroVisitOptions(int maxMacroVisitOptions) {
            this.maxMacroVisitOptions = maxMacroVisitOptions;
        }

        public int getMaxPerRegionFeatureType() {
            return maxPerRegionFeatureType <= 0 ? 3 : maxPerRegionFeatureType;
        }

        public void setMaxPerRegionFeatureType(int maxPerRegionFeatureType) {
            this.maxPerRegionFeatureType = maxPerRegionFeatureType;
        }

        public double getRegionCellSizeM() {
            return regionCellSizeM <= 0 ? 2500 : regionCellSizeM;
        }

        public void setRegionCellSizeM(double regionCellSizeM) {
            this.regionCellSizeM = regionCellSizeM;
        }
    }

    public static class Ranking {
        private double strategyMatch = 0.20;
        private double depthMatch = 0.15;
        private double featureConfidence = 0.15;
        private double timeWindowMatch = 0.10;
        private double gearCompatibility = 0.10;
        private double weatherCompatibility = 0.10;
        private double travelAccess = 0.10;
        private double historicalPerformance = 0.10;

        public double getStrategyMatch() {
            return strategyMatch;
        }

        public void setStrategyMatch(double strategyMatch) {
            this.strategyMatch = strategyMatch;
        }

        public double getDepthMatch() {
            return depthMatch;
        }

        public void setDepthMatch(double depthMatch) {
            this.depthMatch = depthMatch;
        }

        public double getFeatureConfidence() {
            return featureConfidence;
        }

        public void setFeatureConfidence(double featureConfidence) {
            this.featureConfidence = featureConfidence;
        }

        public double getTimeWindowMatch() {
            return timeWindowMatch;
        }

        public void setTimeWindowMatch(double timeWindowMatch) {
            this.timeWindowMatch = timeWindowMatch;
        }

        public double getGearCompatibility() {
            return gearCompatibility;
        }

        public void setGearCompatibility(double gearCompatibility) {
            this.gearCompatibility = gearCompatibility;
        }

        public double getWeatherCompatibility() {
            return weatherCompatibility;
        }

        public void setWeatherCompatibility(double weatherCompatibility) {
            this.weatherCompatibility = weatherCompatibility;
        }

        public double getTravelAccess() {
            return travelAccess;
        }

        public void setTravelAccess(double travelAccess) {
            this.travelAccess = travelAccess;
        }

        public double getHistoricalPerformance() {
            return historicalPerformance;
        }

        public void setHistoricalPerformance(double historicalPerformance) {
            this.historicalPerformance = historicalPerformance;
        }

        public double sum() {
            return strategyMatch + depthMatch + featureConfidence + timeWindowMatch
                    + gearCompatibility + weatherCompatibility + travelAccess + historicalPerformance;
        }
    }

    public static class Schedule {
        private int minSpotMinutes = 20;
        private int maxSpotMinutes = 90;
        private int maxWaypoints = 5;
        private int minWaypoints = 1;
        private int returnBufferMinutes = 15;
        private int slotMinutes = 15;
        private List<Integer> dwellOptionsMinutes = new ArrayList<>(List.of(20, 30, 45, 60, 75, 90));
        private List<Integer> waitOptionsMinutes = new ArrayList<>(List.of(15, 30));
        private double waitPenalty = 0.06;
        private int maxTotalWaitMinutes = 60;
        private double whyThisTimeMinDelta = 0.08;
        private int whyThisTimeHorizonSlots = 8;
        private int beamWidth = 8;
        private double dwellDecay = 0.85;
        private int maxZoneVisitMinutes = 180;
        private int maxZoneEntries = 2;
        private List<Integer> zonePackageMinutes = new ArrayList<>(List.of(45, 90, 135, 180));
        private List<Integer> pointDwellMinutes = new ArrayList<>(List.of(15, 20, 30, 45));
        private List<Integer> pathDwellMinutes = new ArrayList<>(List.of(30, 45, 60, 90));

        public int getMinSpotMinutes() {
            return minSpotMinutes;
        }

        public void setMinSpotMinutes(int minSpotMinutes) {
            this.minSpotMinutes = minSpotMinutes;
        }

        public int getMaxSpotMinutes() {
            return maxSpotMinutes;
        }

        public void setMaxSpotMinutes(int maxSpotMinutes) {
            this.maxSpotMinutes = maxSpotMinutes;
        }

        public int getMaxWaypoints() {
            return maxWaypoints;
        }

        public void setMaxWaypoints(int maxWaypoints) {
            this.maxWaypoints = maxWaypoints;
        }

        public int getMinWaypoints() {
            return minWaypoints;
        }

        public void setMinWaypoints(int minWaypoints) {
            this.minWaypoints = minWaypoints;
        }

        public int getReturnBufferMinutes() {
            return returnBufferMinutes;
        }

        public void setReturnBufferMinutes(int returnBufferMinutes) {
            this.returnBufferMinutes = returnBufferMinutes;
        }

        public int getSlotMinutes() {
            return slotMinutes;
        }

        public void setSlotMinutes(int slotMinutes) {
            this.slotMinutes = slotMinutes <= 0 ? 15 : slotMinutes;
        }

        public List<Integer> getDwellOptionsMinutes() {
            return dwellOptionsMinutes;
        }

        public void setDwellOptionsMinutes(List<Integer> dwellOptionsMinutes) {
            this.dwellOptionsMinutes = dwellOptionsMinutes == null || dwellOptionsMinutes.isEmpty()
                    ? new ArrayList<>(List.of(20, 30, 45, 60, 75, 90))
                    : new ArrayList<>(dwellOptionsMinutes);
        }

        public List<Integer> getWaitOptionsMinutes() {
            return waitOptionsMinutes;
        }

        public void setWaitOptionsMinutes(List<Integer> waitOptionsMinutes) {
            this.waitOptionsMinutes = waitOptionsMinutes == null || waitOptionsMinutes.isEmpty()
                    ? new ArrayList<>(List.of(15, 30))
                    : new ArrayList<>(waitOptionsMinutes);
        }

        public double getWaitPenalty() {
            return waitPenalty;
        }

        public void setWaitPenalty(double waitPenalty) {
            this.waitPenalty = waitPenalty;
        }

        public int getMaxTotalWaitMinutes() {
            return maxTotalWaitMinutes;
        }

        public void setMaxTotalWaitMinutes(int maxTotalWaitMinutes) {
            this.maxTotalWaitMinutes = maxTotalWaitMinutes;
        }

        public double getWhyThisTimeMinDelta() {
            return whyThisTimeMinDelta;
        }

        public void setWhyThisTimeMinDelta(double whyThisTimeMinDelta) {
            this.whyThisTimeMinDelta = whyThisTimeMinDelta;
        }

        public int getWhyThisTimeHorizonSlots() {
            return whyThisTimeHorizonSlots;
        }

        public void setWhyThisTimeHorizonSlots(int whyThisTimeHorizonSlots) {
            this.whyThisTimeHorizonSlots = whyThisTimeHorizonSlots;
        }

        public int getBeamWidth() {
            return beamWidth;
        }

        public void setBeamWidth(int beamWidth) {
            this.beamWidth = beamWidth <= 0 ? 8 : beamWidth;
        }

        public double getDwellDecay() {
            return dwellDecay;
        }

        public void setDwellDecay(double dwellDecay) {
            this.dwellDecay = dwellDecay;
        }

        public int getMaxZoneVisitMinutes() {
            return maxZoneVisitMinutes;
        }

        public void setMaxZoneVisitMinutes(int maxZoneVisitMinutes) {
            this.maxZoneVisitMinutes = maxZoneVisitMinutes <= 0 ? 180 : maxZoneVisitMinutes;
        }

        public int getMaxZoneEntries() {
            return maxZoneEntries;
        }

        public void setMaxZoneEntries(int maxZoneEntries) {
            this.maxZoneEntries = maxZoneEntries <= 0 ? 2 : maxZoneEntries;
        }

        public List<Integer> getZonePackageMinutes() {
            return zonePackageMinutes;
        }

        public void setZonePackageMinutes(List<Integer> zonePackageMinutes) {
            this.zonePackageMinutes = zonePackageMinutes == null || zonePackageMinutes.isEmpty()
                    ? new ArrayList<>(List.of(45, 90, 135, 180))
                    : new ArrayList<>(zonePackageMinutes);
        }

        public List<Integer> getPointDwellMinutes() {
            return pointDwellMinutes;
        }

        public void setPointDwellMinutes(List<Integer> pointDwellMinutes) {
            this.pointDwellMinutes = pointDwellMinutes == null || pointDwellMinutes.isEmpty()
                    ? new ArrayList<>(List.of(15, 20, 30, 45))
                    : new ArrayList<>(pointDwellMinutes);
        }

        public List<Integer> getPathDwellMinutes() {
            return pathDwellMinutes;
        }

        public void setPathDwellMinutes(List<Integer> pathDwellMinutes) {
            this.pathDwellMinutes = pathDwellMinutes == null || pathDwellMinutes.isEmpty()
                    ? new ArrayList<>(List.of(30, 45, 60, 90))
                    : new ArrayList<>(pathDwellMinutes);
        }
    }

    public static class Search {
        private int minBeamWidth = 4;
        private int maxBeamWidth = 24;
        private int defaultBeamWidth = 16;
        private int minLookaheadHorizon = 3;
        private int maxLookaheadHorizon = 5;
        private int defaultLookaheadHorizon = 4;
        private int minEffectiveStops = 2;
        private int hardMaxStops = 10;
        private int fullRouteMaxStops = 5;
        private int maxExpansions = 20_000;
        private double futurePotentialLambda = 0.2;
        private int commitPrefixStops = 1;
        private long maxWallClockMs = 5_000;

        public int getMinBeamWidth() {
            return minBeamWidth <= 0 ? 4 : minBeamWidth;
        }

        public void setMinBeamWidth(int minBeamWidth) {
            this.minBeamWidth = minBeamWidth;
        }

        public int getMaxBeamWidth() {
            return maxBeamWidth <= 0 ? 24 : maxBeamWidth;
        }

        public void setMaxBeamWidth(int maxBeamWidth) {
            this.maxBeamWidth = maxBeamWidth;
        }

        public int getDefaultBeamWidth() {
            return defaultBeamWidth <= 0 ? 16 : defaultBeamWidth;
        }

        public void setDefaultBeamWidth(int defaultBeamWidth) {
            this.defaultBeamWidth = defaultBeamWidth;
        }

        public int getMinLookaheadHorizon() {
            return minLookaheadHorizon <= 0 ? 3 : minLookaheadHorizon;
        }

        public void setMinLookaheadHorizon(int minLookaheadHorizon) {
            this.minLookaheadHorizon = minLookaheadHorizon;
        }

        public int getMaxLookaheadHorizon() {
            return maxLookaheadHorizon <= 0 ? 5 : maxLookaheadHorizon;
        }

        public void setMaxLookaheadHorizon(int maxLookaheadHorizon) {
            this.maxLookaheadHorizon = maxLookaheadHorizon;
        }

        public int getDefaultLookaheadHorizon() {
            return defaultLookaheadHorizon <= 0 ? 4 : defaultLookaheadHorizon;
        }

        public void setDefaultLookaheadHorizon(int defaultLookaheadHorizon) {
            this.defaultLookaheadHorizon = defaultLookaheadHorizon;
        }

        public int getMinEffectiveStops() {
            return minEffectiveStops <= 0 ? 2 : minEffectiveStops;
        }

        public void setMinEffectiveStops(int minEffectiveStops) {
            this.minEffectiveStops = minEffectiveStops;
        }

        /**
         * @deprecated use {@link #getMinEffectiveStops()}
         */
        @Deprecated
        public int getMinMaxStops() {
            return getMinEffectiveStops();
        }

        /**
         * @deprecated use {@link #setMinEffectiveStops(int)}
         */
        @Deprecated
        public void setMinMaxStops(int minMaxStops) {
            this.minEffectiveStops = minMaxStops;
        }

        public int getHardMaxStops() {
            return hardMaxStops <= 0 ? 10 : hardMaxStops;
        }

        public void setHardMaxStops(int hardMaxStops) {
            this.hardMaxStops = hardMaxStops;
        }

        public int getFullRouteMaxStops() {
            return fullRouteMaxStops <= 0 ? 5 : fullRouteMaxStops;
        }

        public void setFullRouteMaxStops(int fullRouteMaxStops) {
            this.fullRouteMaxStops = fullRouteMaxStops;
        }

        public int getMaxExpansions() {
            return maxExpansions <= 0 ? 20_000 : maxExpansions;
        }

        public void setMaxExpansions(int maxExpansions) {
            this.maxExpansions = maxExpansions;
        }

        public long getMaxWallClockMs() {
            return maxWallClockMs < 0 ? 5_000 : maxWallClockMs;
        }

        public void setMaxWallClockMs(long maxWallClockMs) {
            this.maxWallClockMs = maxWallClockMs;
        }

        public double getFuturePotentialLambda() {
            return futurePotentialLambda < 0 ? 0.2 : futurePotentialLambda;
        }

        public void setFuturePotentialLambda(double futurePotentialLambda) {
            this.futurePotentialLambda = futurePotentialLambda;
        }

        public int getCommitPrefixStops() {
            return commitPrefixStops <= 0 ? 1 : commitPrefixStops;
        }

        public void setCommitPrefixStops(int commitPrefixStops) {
            this.commitPrefixStops = commitPrefixStops;
        }
    }

    public static class Tactics {
        private int staleTimeoutSeconds = 900;

        public int getStaleTimeoutSeconds() {
            return staleTimeoutSeconds <= 0 ? 900 : staleTimeoutSeconds;
        }

        public void setStaleTimeoutSeconds(int staleTimeoutSeconds) {
            this.staleTimeoutSeconds = staleTimeoutSeconds;
        }
    }

    public static class Environment {
        private Solar solar = new Solar();
        private Wind wind = new Wind();
        private Temperature temperature = new Temperature();

        public Solar getSolar() {
            return solar;
        }

        public void setSolar(Solar solar) {
            this.solar = solar == null ? new Solar() : solar;
        }

        public Wind getWind() {
            return wind;
        }

        public void setWind(Wind wind) {
            this.wind = wind == null ? new Wind() : wind;
        }

        public Temperature getTemperature() {
            return temperature;
        }

        public void setTemperature(Temperature temperature) {
            this.temperature = temperature == null ? new Temperature() : temperature;
        }

        public static class Solar {
            private double maxWeight = 0.12;
            private double lowRadiationThreshold = 120;
            private double highRadiationThreshold = 700;
            private double overcastCloudPercent = 85;
            private double clearCloudPercent = 25;

            public double getMaxWeight() {
                return maxWeight;
            }

            public void setMaxWeight(double maxWeight) {
                this.maxWeight = maxWeight;
            }

            public double getLowRadiationThreshold() {
                return lowRadiationThreshold;
            }

            public void setLowRadiationThreshold(double lowRadiationThreshold) {
                this.lowRadiationThreshold = lowRadiationThreshold;
            }

            public double getHighRadiationThreshold() {
                return highRadiationThreshold;
            }

            public void setHighRadiationThreshold(double highRadiationThreshold) {
                this.highRadiationThreshold = highRadiationThreshold;
            }

            public double getOvercastCloudPercent() {
                return overcastCloudPercent;
            }

            public void setOvercastCloudPercent(double overcastCloudPercent) {
                this.overcastCloudPercent = overcastCloudPercent;
            }

            public double getClearCloudPercent() {
                return clearCloudPercent;
            }

            public void setClearCloudPercent(double clearCloudPercent) {
                this.clearCloudPercent = clearCloudPercent;
            }
        }

        public static class Wind {
            private double fishingMaxWeight = 0.08;

            public double getFishingMaxWeight() {
                return fishingMaxWeight;
            }

            public void setFishingMaxWeight(double fishingMaxWeight) {
                this.fishingMaxWeight = fishingMaxWeight;
            }
        }

        public static class Temperature {
            private double maxWeight = 0.04;
            private double airProxyWeight = 0.35;

            public double getMaxWeight() {
                return maxWeight;
            }

            public void setMaxWeight(double maxWeight) {
                this.maxWeight = maxWeight;
            }

            public double getAirProxyWeight() {
                return airProxyWeight;
            }

            public void setAirProxyWeight(double airProxyWeight) {
                this.airProxyWeight = airProxyWeight;
            }
        }
    }

    public static class Travel {
        private double detourFactor = 1.35;
        private double landCrossingDetourFactor = 2.2;
        private double defaultBoatKmh = 12;
        private double defaultShoreKmh = 4;
        private double maxOneWayFractionOfTrip = 0.25;

        public double getDetourFactor() {
            return detourFactor;
        }

        public void setDetourFactor(double detourFactor) {
            this.detourFactor = detourFactor;
        }

        public double getLandCrossingDetourFactor() {
            return landCrossingDetourFactor;
        }

        public void setLandCrossingDetourFactor(double landCrossingDetourFactor) {
            this.landCrossingDetourFactor = landCrossingDetourFactor;
        }

        public double getDefaultBoatKmh() {
            return defaultBoatKmh;
        }

        public void setDefaultBoatKmh(double defaultBoatKmh) {
            this.defaultBoatKmh = defaultBoatKmh;
        }

        public double getDefaultShoreKmh() {
            return defaultShoreKmh;
        }

        public void setDefaultShoreKmh(double defaultShoreKmh) {
            this.defaultShoreKmh = defaultShoreKmh;
        }

        public double getMaxOneWayFractionOfTrip() {
            return maxOneWayFractionOfTrip;
        }

        public void setMaxOneWayFractionOfTrip(double maxOneWayFractionOfTrip) {
            this.maxOneWayFractionOfTrip = maxOneWayFractionOfTrip;
        }
    }

    public static class Access {
        private boolean requireAccessForBoat = false;
        private double shoreUnverifiedAccessibilityScore = 0.35;

        public boolean isRequireAccessForBoat() {
            return requireAccessForBoat;
        }

        public void setRequireAccessForBoat(boolean requireAccessForBoat) {
            this.requireAccessForBoat = requireAccessForBoat;
        }

        public double getShoreUnverifiedAccessibilityScore() {
            return shoreUnverifiedAccessibilityScore;
        }

        public void setShoreUnverifiedAccessibilityScore(double shoreUnverifiedAccessibilityScore) {
            this.shoreUnverifiedAccessibilityScore = shoreUnverifiedAccessibilityScore;
        }
    }

    public static class BoatLimits {
        private Map<BoatType, Double> maxOneWayKm = defaultBoatLimits();

        public Map<BoatType, Double> getMaxOneWayKm() {
            return maxOneWayKm;
        }

        public void setMaxOneWayKm(Map<BoatType, Double> maxOneWayKm) {
            this.maxOneWayKm = maxOneWayKm == null ? defaultBoatLimits() : maxOneWayKm;
        }

        public double maxOneWayKm(BoatType type) {
            BoatType key = type == null ? BoatType.OTHER : type;
            Double value = maxOneWayKm.get(key);
            if (value == null) {
                value = maxOneWayKm.get(BoatType.OTHER);
            }
            return value == null ? 8.0 : value;
        }

        private static Map<BoatType, Double> defaultBoatLimits() {
            EnumMap<BoatType, Double> limits = new EnumMap<>(BoatType.class);
            limits.put(BoatType.KAYAK, 4.0);
            limits.put(BoatType.CANOE, 5.0);
            limits.put(BoatType.INFLATABLE, 6.0);
            limits.put(BoatType.PONTOON, 15.0);
            limits.put(BoatType.FISHING_BOAT, 20.0);
            limits.put(BoatType.OTHER, 8.0);
            return limits;
        }
    }

    public static class Spatial {
        private String derivationVersion = "spatial-targets-v3";
        private String zoneBuilderVersion = "physical-zones-v4";
        private String navigationVersion = "water-nav-v3";
        private double concaveHullEdgeLengthRatio = 0.35;
        private double sliverMinAreaM2 = 80;
        private double corridorWidthShoreM = 25;
        private double corridorWidthDropOffM = 20;
        private double corridorWidthDefaultM = 18;
        private double maxSegmentLengthM = 600;
        private double clusterConnectM = 220;
        private double clusterMaxDiameterM = 900;
        private double zoneNeighborSearchRadiusM = 550;
        private double zoneWaterPathJoinMaxM = 400;
        private double zoneMaxWaterPathDiameterM = 0;
        private double zoneMaxInternalGapM = 450;
        private double zoneMinCoverageDensity = 6.0;
        private double pathCoverageUnitM = 80;
        private double navMinWaterCoverage = 0.55;
        private double navMaxIslandFraction = 0.15;
        private int navTileSizeCells = 128;
        private int clusterMinMembers = 3;
        private int clusterMaxMembers = 8;
        private double localPathCellSizeM = 25;
        private int localPathMaxCells = 40_000;
        private double internalCruiseKmh = 6;
        /** Micros within this distance share one static-plan dwell when land does not separate them. */
        private double castingOpportunityMeters = 40;
        private int maxZonePortals = 4;
        private int maxPortalPairsPerZone = 8;
        private double sampleAlongM = 40;
        private int maxSamplesPerGeometry = 9;
        private int pairwiseFullNodeLimit = 48;
        private int pairwiseFullPortalLimit = 16;
        private int maxNavNodesPerZone = 2500;
        private double maxSkippedTargetRatio = 0.15;
        private double maxSkippedPartitionRatio = 0.20;

        public String getDerivationVersion() {
            return derivationVersion;
        }

        public void setDerivationVersion(String derivationVersion) {
            this.derivationVersion = derivationVersion == null || derivationVersion.isBlank()
                    ? "spatial-targets-v3"
                    : derivationVersion;
        }

        public String getZoneBuilderVersion() {
            return zoneBuilderVersion;
        }

        public void setZoneBuilderVersion(String zoneBuilderVersion) {
            this.zoneBuilderVersion = zoneBuilderVersion == null || zoneBuilderVersion.isBlank()
                    ? "physical-zones-v4"
                    : zoneBuilderVersion;
        }

        public String getNavigationVersion() {
            return navigationVersion;
        }

        public void setNavigationVersion(String navigationVersion) {
            this.navigationVersion = navigationVersion == null || navigationVersion.isBlank()
                    ? "water-nav-v3"
                    : navigationVersion;
        }

        public double getConcaveHullEdgeLengthRatio() {
            return concaveHullEdgeLengthRatio;
        }

        public void setConcaveHullEdgeLengthRatio(double concaveHullEdgeLengthRatio) {
            this.concaveHullEdgeLengthRatio = concaveHullEdgeLengthRatio <= 0 ? 0.35 : concaveHullEdgeLengthRatio;
        }

        public double getSliverMinAreaM2() {
            return sliverMinAreaM2;
        }

        public void setSliverMinAreaM2(double sliverMinAreaM2) {
            this.sliverMinAreaM2 = sliverMinAreaM2;
        }

        public double getCorridorWidthShoreM() {
            return corridorWidthShoreM;
        }

        public void setCorridorWidthShoreM(double corridorWidthShoreM) {
            this.corridorWidthShoreM = corridorWidthShoreM;
        }

        public double getCorridorWidthDropOffM() {
            return corridorWidthDropOffM;
        }

        public void setCorridorWidthDropOffM(double corridorWidthDropOffM) {
            this.corridorWidthDropOffM = corridorWidthDropOffM;
        }

        public double getCorridorWidthDefaultM() {
            return corridorWidthDefaultM;
        }

        public void setCorridorWidthDefaultM(double corridorWidthDefaultM) {
            this.corridorWidthDefaultM = corridorWidthDefaultM;
        }

        public double getMaxSegmentLengthM() {
            return maxSegmentLengthM;
        }

        public void setMaxSegmentLengthM(double maxSegmentLengthM) {
            this.maxSegmentLengthM = maxSegmentLengthM;
        }

        public double getClusterConnectM() {
            return clusterConnectM;
        }

        public void setClusterConnectM(double clusterConnectM) {
            this.clusterConnectM = clusterConnectM;
        }

        public double getClusterMaxDiameterM() {
            return clusterMaxDiameterM;
        }

        public void setClusterMaxDiameterM(double clusterMaxDiameterM) {
            this.clusterMaxDiameterM = clusterMaxDiameterM;
        }

        public double getZoneNeighborSearchRadiusM() {
            return zoneNeighborSearchRadiusM <= 0 ? 550 : zoneNeighborSearchRadiusM;
        }

        public void setZoneNeighborSearchRadiusM(double zoneNeighborSearchRadiusM) {
            this.zoneNeighborSearchRadiusM = zoneNeighborSearchRadiusM;
        }

        public double getZoneWaterPathJoinMaxM() {
            return zoneWaterPathJoinMaxM <= 0 ? 400 : zoneWaterPathJoinMaxM;
        }

        public void setZoneWaterPathJoinMaxM(double zoneWaterPathJoinMaxM) {
            this.zoneWaterPathJoinMaxM = zoneWaterPathJoinMaxM;
        }

        public double getZoneMaxWaterPathDiameterM() {
            return zoneMaxWaterPathDiameterM > 0 ? zoneMaxWaterPathDiameterM
                    : (clusterMaxDiameterM > 0 ? clusterMaxDiameterM : 900);
        }

        public void setZoneMaxWaterPathDiameterM(double zoneMaxWaterPathDiameterM) {
            this.zoneMaxWaterPathDiameterM = zoneMaxWaterPathDiameterM;
        }

        public double getZoneMaxInternalGapM() {
            return zoneMaxInternalGapM <= 0 ? 450 : zoneMaxInternalGapM;
        }

        public void setZoneMaxInternalGapM(double zoneMaxInternalGapM) {
            this.zoneMaxInternalGapM = zoneMaxInternalGapM;
        }

        public double getZoneMinCoverageDensity() {
            return zoneMinCoverageDensity <= 0 ? 6.0 : zoneMinCoverageDensity;
        }

        public void setZoneMinCoverageDensity(double zoneMinCoverageDensity) {
            this.zoneMinCoverageDensity = zoneMinCoverageDensity;
        }

        public double getPathCoverageUnitM() {
            return pathCoverageUnitM <= 0 ? 80 : pathCoverageUnitM;
        }

        public void setPathCoverageUnitM(double pathCoverageUnitM) {
            this.pathCoverageUnitM = pathCoverageUnitM;
        }

        public double getNavMinWaterCoverage() {
            return navMinWaterCoverage <= 0 ? 0.55 : navMinWaterCoverage;
        }

        public void setNavMinWaterCoverage(double navMinWaterCoverage) {
            this.navMinWaterCoverage = navMinWaterCoverage;
        }

        public double getNavMaxIslandFraction() {
            return navMaxIslandFraction < 0 ? 0.15 : navMaxIslandFraction;
        }

        public void setNavMaxIslandFraction(double navMaxIslandFraction) {
            this.navMaxIslandFraction = navMaxIslandFraction;
        }

        public int getNavTileSizeCells() {
            return navTileSizeCells <= 0 ? 128 : navTileSizeCells;
        }

        public void setNavTileSizeCells(int navTileSizeCells) {
            this.navTileSizeCells = navTileSizeCells;
        }

        public int getClusterMinMembers() {
            return clusterMinMembers;
        }

        public void setClusterMinMembers(int clusterMinMembers) {
            this.clusterMinMembers = clusterMinMembers;
        }

        public int getClusterMaxMembers() {
            return clusterMaxMembers;
        }

        public void setClusterMaxMembers(int clusterMaxMembers) {
            this.clusterMaxMembers = clusterMaxMembers;
        }

        public double getLocalPathCellSizeM() {
            return localPathCellSizeM;
        }

        public void setLocalPathCellSizeM(double localPathCellSizeM) {
            this.localPathCellSizeM = localPathCellSizeM <= 0 ? 25 : localPathCellSizeM;
        }

        public int getLocalPathMaxCells() {
            return localPathMaxCells;
        }

        public void setLocalPathMaxCells(int localPathMaxCells) {
            this.localPathMaxCells = localPathMaxCells <= 0 ? 40_000 : localPathMaxCells;
        }

        public double getInternalCruiseKmh() {
            return internalCruiseKmh;
        }

        public void setInternalCruiseKmh(double internalCruiseKmh) {
            this.internalCruiseKmh = internalCruiseKmh <= 0 ? 6 : internalCruiseKmh;
        }

        public double getCastingOpportunityMeters() {
            return castingOpportunityMeters;
        }

        public void setCastingOpportunityMeters(double castingOpportunityMeters) {
            this.castingOpportunityMeters = castingOpportunityMeters <= 0 ? 40 : castingOpportunityMeters;
        }

        public int getMaxZonePortals() {
            return maxZonePortals;
        }

        public void setMaxZonePortals(int maxZonePortals) {
            this.maxZonePortals = maxZonePortals <= 0 ? 4 : maxZonePortals;
        }

        public int getMaxPortalPairsPerZone() {
            return maxPortalPairsPerZone;
        }

        public void setMaxPortalPairsPerZone(int maxPortalPairsPerZone) {
            this.maxPortalPairsPerZone = maxPortalPairsPerZone <= 0 ? 8 : maxPortalPairsPerZone;
        }

        public double getSampleAlongM() {
            return sampleAlongM;
        }

        public void setSampleAlongM(double sampleAlongM) {
            this.sampleAlongM = sampleAlongM <= 0 ? 40 : sampleAlongM;
        }

        public int getMaxSamplesPerGeometry() {
            return maxSamplesPerGeometry;
        }

        public void setMaxSamplesPerGeometry(int maxSamplesPerGeometry) {
            this.maxSamplesPerGeometry = maxSamplesPerGeometry <= 0 ? 9 : maxSamplesPerGeometry;
        }

        public int getPairwiseFullNodeLimit() {
            return pairwiseFullNodeLimit;
        }

        public void setPairwiseFullNodeLimit(int pairwiseFullNodeLimit) {
            this.pairwiseFullNodeLimit = pairwiseFullNodeLimit <= 0 ? 48 : pairwiseFullNodeLimit;
        }

        public int getPairwiseFullPortalLimit() {
            return pairwiseFullPortalLimit;
        }

        public void setPairwiseFullPortalLimit(int pairwiseFullPortalLimit) {
            this.pairwiseFullPortalLimit = pairwiseFullPortalLimit <= 0 ? 16 : pairwiseFullPortalLimit;
        }

        public int getMaxNavNodesPerZone() {
            return maxNavNodesPerZone;
        }

        public void setMaxNavNodesPerZone(int maxNavNodesPerZone) {
            this.maxNavNodesPerZone = maxNavNodesPerZone <= 0 ? 2500 : maxNavNodesPerZone;
        }

        public double getMaxSkippedTargetRatio() {
            return maxSkippedTargetRatio;
        }

        public void setMaxSkippedTargetRatio(double maxSkippedTargetRatio) {
            this.maxSkippedTargetRatio = clampRatio(maxSkippedTargetRatio, 0.15);
        }

        public double getMaxSkippedPartitionRatio() {
            return maxSkippedPartitionRatio;
        }

        public void setMaxSkippedPartitionRatio(double maxSkippedPartitionRatio) {
            this.maxSkippedPartitionRatio = clampRatio(maxSkippedPartitionRatio, 0.20);
        }

        private static double clampRatio(double value, double fallback) {
            if (Double.isNaN(value) || value < 0 || value > 1) {
                return fallback;
            }
            return value;
        }
    }

    public static class Safety {
        private double windHardRejectKmh = 40;
        private double windPenaltyKmh = 25;

        public double getWindHardRejectKmh() {
            return windHardRejectKmh;
        }

        public void setWindHardRejectKmh(double windHardRejectKmh) {
            this.windHardRejectKmh = windHardRejectKmh;
        }

        public double getWindPenaltyKmh() {
            return windPenaltyKmh;
        }

        public void setWindPenaltyKmh(double windPenaltyKmh) {
            this.windPenaltyKmh = windPenaltyKmh;
        }
    }
}
