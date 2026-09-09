package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;

import org.springframework.stereotype.Component;

@Component
public class BoatCapabilityFallback {

    private final BoatCapabilityProperties properties;

    public BoatCapabilityFallback(BoatCapabilityProperties properties) {
        this.properties = properties;
    }

    public CapabilityMetric<Double> cruise(Boat boat) {
        BoatCapabilityProperties.NumericBand band = band(boat);
        if (band != null && band.getCruiseSpeedKmh() != null) {
            return CapabilityMetric.of(band.getCruiseSpeedKmh(), nvl(band.getCruiseConfidence(), 0.45), CapabilitySource.CONSERVATIVE_FALLBACK);
        }
        BoatCapabilityProperties.MetricDefaults propulsion = propulsion(boat);
        Double speed = first(propulsion.getCruiseSpeedKmh(), properties.getFallback().getDefaults().getCruiseSpeedKmh(), 8.0);
        Double confidence = first(propulsion.getCruiseConfidence(), properties.getFallback().getDefaults().getCruiseConfidence(), 0.4);
        return CapabilityMetric.of(speed, confidence, CapabilitySource.CONSERVATIVE_FALLBACK);
    }

    public CapabilityMetric<Double> range(Boat boat) {
        BoatCapabilityProperties.NumericBand band = band(boat);
        if (band != null && band.getPracticalRangeKm() != null) {
            return CapabilityMetric.of(
                    band.getPracticalRangeKm(),
                    nvl(band.getRangeConfidence(), 0.25),
                    CapabilitySource.CONSERVATIVE_FALLBACK
            );
        }
        BoatCapabilityProperties.MetricDefaults propulsion = propulsion(boat);
        return CapabilityMetric.of(
                first(propulsion.getPracticalRangeKm(), properties.getFallback().getDefaults().getPracticalRangeKm(), null),
                first(propulsion.getRangeConfidence(), properties.getFallback().getDefaults().getRangeConfidence(), 0.25),
                CapabilitySource.CONSERVATIVE_FALLBACK
        );
    }

    public CapabilityMetric<WindWaveCapability> wind(Boat boat) {
        BoatCapabilityProperties.BoatTypeDefaults typeDefaults =
                properties.getFallback().getByBoatType().get(boat.getType());
        if (typeDefaults != null && typeDefaults.getWindWave() != null) {
            return CapabilityMetric.of(typeDefaults.getWindWave(), 0.55, CapabilitySource.CONSERVATIVE_FALLBACK);
        }
        BoatCapabilityProperties.NumericBand band = band(boat);
        if (band != null && band.getWindWave() != null) {
            return CapabilityMetric.of(band.getWindWave(), nvl(band.getWindConfidence(), 0.45), CapabilitySource.CONSERVATIVE_FALLBACK);
        }
        BoatCapabilityProperties.MetricDefaults propulsion = propulsion(boat);
        WindWaveCapability value = first(
                propulsion.getWindWave(),
                properties.getFallback().getDefaults().getWindWave(),
                WindWaveCapability.LOW
        );
        Double confidence = first(propulsion.getWindConfidence(), properties.getFallback().getDefaults().getWindConfidence(), 0.45);
        return CapabilityMetric.of(value, confidence, CapabilitySource.CONSERVATIVE_FALLBACK);
    }

    private BoatCapabilityProperties.MetricDefaults propulsion(Boat boat) {
        PropulsionType type = boat.getPrimaryTransitPropulsionType();
        BoatCapabilityProperties.MetricDefaults found = properties.getFallback().getByPropulsion().get(type);
        return found == null ? properties.getFallback().getDefaults() : found;
    }

    private BoatCapabilityProperties.NumericBand band(Boat boat) {
        BoatMotor motor = boat.primaryTransitMotor();
        PropulsionType transit = boat.getPrimaryTransitPropulsionType();
        if (transit == PropulsionType.GAS_OUTBOARD && motor != null && motor.horsepower() != null) {
            return match(properties.getFallback().getGasHorsepowerBands(), motor.horsepower().doubleValue());
        }
        if ((transit == PropulsionType.ELECTRIC_TROLLING || transit == PropulsionType.ELECTRIC_OUTBOARD)
                && motor != null && motor.thrustLb() != null) {
            return match(properties.getFallback().getElectricThrustBands(), motor.thrustLb().doubleValue());
        }
        return null;
    }

    private static BoatCapabilityProperties.NumericBand match(
            java.util.List<BoatCapabilityProperties.NumericBand> bands,
            double value
    ) {
        BoatCapabilityProperties.NumericBand chosen = null;
        for (BoatCapabilityProperties.NumericBand band : bands) {
            if (value <= band.getMaxInclusive()) {
                if (chosen == null || band.getMaxInclusive() < chosen.getMaxInclusive()) {
                    chosen = band;
                }
            }
        }
        return chosen;
    }

    @SafeVarargs
    private static <T> T first(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double nvl(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
