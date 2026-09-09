package com.aifishing.boat.capability;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.boat-capability")
public class BoatCapabilityProperties {

    private String resolverVersion = "boat-capability-v1.1";
    private String promptVersion = "boat-capability-v2";
    private String model = "";
    private boolean webSearchEnabled = true;
    private double rangeReserveFraction = 0.30;
    private double lowRangeConfidenceThreshold = 0.50;
    private double maxCruiseSpeedKmh = 80;
    private double maxPracticalRangeKm = 80;
    private Integer cacheDays = null;
    private WindDerate windDerate = new WindDerate();
    private Fallback fallback = new Fallback();

    public String getResolverVersion() {
        return resolverVersion;
    }

    public void setResolverVersion(String resolverVersion) {
        this.resolverVersion = resolverVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public double getRangeReserveFraction() {
        return rangeReserveFraction;
    }

    public void setRangeReserveFraction(double rangeReserveFraction) {
        this.rangeReserveFraction = rangeReserveFraction;
    }

    public double getLowRangeConfidenceThreshold() {
        return lowRangeConfidenceThreshold;
    }

    public void setLowRangeConfidenceThreshold(double lowRangeConfidenceThreshold) {
        this.lowRangeConfidenceThreshold = lowRangeConfidenceThreshold;
    }

    public double getMaxCruiseSpeedKmh() {
        return maxCruiseSpeedKmh;
    }

    public void setMaxCruiseSpeedKmh(double maxCruiseSpeedKmh) {
        this.maxCruiseSpeedKmh = maxCruiseSpeedKmh;
    }

    public double getMaxPracticalRangeKm() {
        return maxPracticalRangeKm;
    }

    public void setMaxPracticalRangeKm(double maxPracticalRangeKm) {
        this.maxPracticalRangeKm = maxPracticalRangeKm;
    }

    public Integer getCacheDays() {
        return cacheDays;
    }

    public void setCacheDays(Integer cacheDays) {
        this.cacheDays = cacheDays;
    }

    public WindDerate getWindDerate() {
        return windDerate;
    }

    public void setWindDerate(WindDerate windDerate) {
        this.windDerate = windDerate == null ? new WindDerate() : windDerate;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback == null ? new Fallback() : fallback;
    }

    public static class WindDerate {
        private Map<WindWaveCapability, Double> penalty = defaultPenalty();

        public Map<WindWaveCapability, Double> getPenalty() {
            return penalty;
        }

        public void setPenalty(Map<WindWaveCapability, Double> penalty) {
            this.penalty = penalty == null ? defaultPenalty() : penalty;
        }

        public double fraction(WindWaveCapability capability) {
            WindWaveCapability key = capability == null ? WindWaveCapability.LOW : capability;
            Double value = penalty.get(key);
            return value == null ? 0.25 : value;
        }

        private static Map<WindWaveCapability, Double> defaultPenalty() {
            EnumMap<WindWaveCapability, Double> map = new EnumMap<>(WindWaveCapability.class);
            map.put(WindWaveCapability.LOW, 0.35);
            map.put(WindWaveCapability.MEDIUM, 0.20);
            map.put(WindWaveCapability.HIGH, 0.10);
            return map;
        }
    }

    public static class Fallback {
        private MetricDefaults defaults = new MetricDefaults();
        private Map<PropulsionType, MetricDefaults> byPropulsion = new EnumMap<>(PropulsionType.class);
        private Map<BoatType, BoatTypeDefaults> byBoatType = new EnumMap<>(BoatType.class);
        private List<NumericBand> gasHorsepowerBands = new ArrayList<>();
        private List<NumericBand> electricThrustBands = new ArrayList<>();

        public MetricDefaults getDefaults() {
            return defaults;
        }

        public void setDefaults(MetricDefaults defaults) {
            this.defaults = defaults == null ? new MetricDefaults() : defaults;
        }

        public Map<PropulsionType, MetricDefaults> getByPropulsion() {
            return byPropulsion;
        }

        public void setByPropulsion(Map<PropulsionType, MetricDefaults> byPropulsion) {
            this.byPropulsion = byPropulsion == null ? new EnumMap<>(PropulsionType.class) : byPropulsion;
        }

        public Map<BoatType, BoatTypeDefaults> getByBoatType() {
            return byBoatType;
        }

        public void setByBoatType(Map<BoatType, BoatTypeDefaults> byBoatType) {
            this.byBoatType = byBoatType == null ? new EnumMap<>(BoatType.class) : byBoatType;
        }

        public List<NumericBand> getGasHorsepowerBands() {
            return gasHorsepowerBands;
        }

        public void setGasHorsepowerBands(List<NumericBand> gasHorsepowerBands) {
            this.gasHorsepowerBands = gasHorsepowerBands == null ? new ArrayList<>() : gasHorsepowerBands;
        }

        public List<NumericBand> getElectricThrustBands() {
            return electricThrustBands;
        }

        public void setElectricThrustBands(List<NumericBand> electricThrustBands) {
            this.electricThrustBands = electricThrustBands == null ? new ArrayList<>() : electricThrustBands;
        }
    }

    public static class MetricDefaults {
        private Double cruiseSpeedKmh = 8.0;
        private Double cruiseConfidence = 0.40;
        private Double practicalRangeKm = null;
        private Double rangeConfidence = 0.25;
        private WindWaveCapability windWave = WindWaveCapability.LOW;
        private Double windConfidence = 0.45;

        public Double getCruiseSpeedKmh() {
            return cruiseSpeedKmh;
        }

        public void setCruiseSpeedKmh(Double cruiseSpeedKmh) {
            this.cruiseSpeedKmh = cruiseSpeedKmh;
        }

        public Double getCruiseConfidence() {
            return cruiseConfidence;
        }

        public void setCruiseConfidence(Double cruiseConfidence) {
            this.cruiseConfidence = cruiseConfidence;
        }

        public Double getPracticalRangeKm() {
            return practicalRangeKm;
        }

        public void setPracticalRangeKm(Double practicalRangeKm) {
            this.practicalRangeKm = practicalRangeKm;
        }

        public Double getRangeConfidence() {
            return rangeConfidence;
        }

        public void setRangeConfidence(Double rangeConfidence) {
            this.rangeConfidence = rangeConfidence;
        }

        public WindWaveCapability getWindWave() {
            return windWave;
        }

        public void setWindWave(WindWaveCapability windWave) {
            this.windWave = windWave;
        }

        public Double getWindConfidence() {
            return windConfidence;
        }

        public void setWindConfidence(Double windConfidence) {
            this.windConfidence = windConfidence;
        }
    }

    public static class BoatTypeDefaults {
        private WindWaveCapability windWave;
        private Double maxCruiseSpeedKmh;

        public WindWaveCapability getWindWave() {
            return windWave;
        }

        public void setWindWave(WindWaveCapability windWave) {
            this.windWave = windWave;
        }

        public Double getMaxCruiseSpeedKmh() {
            return maxCruiseSpeedKmh;
        }

        public void setMaxCruiseSpeedKmh(Double maxCruiseSpeedKmh) {
            this.maxCruiseSpeedKmh = maxCruiseSpeedKmh;
        }
    }

    public static class NumericBand {
        private double maxInclusive;
        private Double cruiseSpeedKmh;
        private Double cruiseConfidence;
        private Double practicalRangeKm;
        private Double rangeConfidence;
        private WindWaveCapability windWave;
        private Double windConfidence;

        public double getMaxInclusive() {
            return maxInclusive;
        }

        public void setMaxInclusive(double maxInclusive) {
            this.maxInclusive = maxInclusive;
        }

        public Double getCruiseSpeedKmh() {
            return cruiseSpeedKmh;
        }

        public void setCruiseSpeedKmh(Double cruiseSpeedKmh) {
            this.cruiseSpeedKmh = cruiseSpeedKmh;
        }

        public Double getCruiseConfidence() {
            return cruiseConfidence;
        }

        public void setCruiseConfidence(Double cruiseConfidence) {
            this.cruiseConfidence = cruiseConfidence;
        }

        public Double getPracticalRangeKm() {
            return practicalRangeKm;
        }

        public void setPracticalRangeKm(Double practicalRangeKm) {
            this.practicalRangeKm = practicalRangeKm;
        }

        public Double getRangeConfidence() {
            return rangeConfidence;
        }

        public void setRangeConfidence(Double rangeConfidence) {
            this.rangeConfidence = rangeConfidence;
        }

        public WindWaveCapability getWindWave() {
            return windWave;
        }

        public void setWindWave(WindWaveCapability windWave) {
            this.windWave = windWave;
        }

        public Double getWindConfidence() {
            return windConfidence;
        }

        public void setWindConfidence(Double windConfidence) {
            this.windConfidence = windConfidence;
        }
    }

    public Map<String, Object> asPublicLimits() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rangeReserveFraction", rangeReserveFraction);
        map.put("lowRangeConfidenceThreshold", lowRangeConfidenceThreshold);
        return map;
    }
}
