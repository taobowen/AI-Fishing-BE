package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class BoatCapabilityValidator {

    private final BoatCapabilityProperties properties;

    public BoatCapabilityValidator(BoatCapabilityProperties properties) {
        this.properties = properties;
    }

    public List<String> validateMetric(String name, Double value, Double confidence, boolean requirePositive) {
        List<String> errors = new ArrayList<>();
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            errors.add(name + " confidence must be between 0 and 1");
        }
        if (value == null) {
            return errors;
        }
        if (requirePositive && value <= 0) {
            errors.add(name + " must be > 0");
        }
        if ("cruiseSpeedKmh".equals(name) && value > properties.getMaxCruiseSpeedKmh()) {
            errors.add("cruiseSpeedKmh exceeds sanity bound " + properties.getMaxCruiseSpeedKmh());
        }
        if ("practicalRangeKm".equals(name) && value > properties.getMaxPracticalRangeKm()) {
            errors.add("practicalRangeKm exceeds sanity bound " + properties.getMaxPracticalRangeKm());
        }
        return errors;
    }

    public List<String> validateCombination(Boat boat, Double cruiseSpeedKmh) {
        List<String> errors = new ArrayList<>();
        if (cruiseSpeedKmh == null) {
            return errors;
        }
        BoatType type = boat.getType();
        PropulsionType transit = boat.getPrimaryTransitPropulsionType();
        BoatMotor motor = boat.primaryTransitMotor();
        double hp = motor != null && motor.horsepower() != null ? motor.horsepower().doubleValue() : 0;
        double thrust = motor != null && motor.thrustLb() != null ? motor.thrustLb().doubleValue() : 0;
        if (type == BoatType.INFLATABLE && transit == PropulsionType.ELECTRIC_TROLLING && thrust <= 80 && cruiseSpeedKmh > 25) {
            errors.add("inflatable + trolling motor cruise speed is implausible");
        }
        if (transit == PropulsionType.PADDLE && cruiseSpeedKmh > 12) {
            errors.add("paddle cruise speed is implausible");
        }
        if (transit == PropulsionType.GAS_OUTBOARD && hp > 0 && hp <= 10 && cruiseSpeedKmh > 45) {
            errors.add("low-HP outboard cruise speed is implausible");
        }
        BoatCapabilityProperties.BoatTypeDefaults typeDefaults =
                properties.getFallback().getByBoatType().get(type);
        if (typeDefaults != null && typeDefaults.getMaxCruiseSpeedKmh() != null
                && cruiseSpeedKmh > typeDefaults.getMaxCruiseSpeedKmh()) {
            errors.add("cruise speed exceeds hull-type sanity bound");
        }
        return errors;
    }

    public List<String> validateWind(WindWaveCapability value, Double confidence) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add("windWaveCapability is required");
        }
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            errors.add("windWave confidence must be between 0 and 1");
        }
        return errors;
    }
}
