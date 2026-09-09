package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.WindWaveCapability;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record BoatCapabilityPriors(
        Double cruiseSpeedKmh,
        Double practicalRangeKm,
        WindWaveCapability windWaveCapability
) {
    public static BoatCapabilityPriors from(Boat boat) {
        if (boat == null) {
            return null;
        }
        Double cruise = decimal(boat.getMeasuredCruiseSpeedKmh());
        Double range = decimal(boat.getComfortableRoundTripRangeKm());
        WindWaveCapability wind = boat.getWindWaveOverride();
        if (cruise == null && range == null && wind == null) {
            return null;
        }
        return new BoatCapabilityPriors(cruise, range, wind);
    }

    public boolean present() {
        return cruiseSpeedKmh != null || practicalRangeKm != null || windWaveCapability != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cruiseSpeedKmh", cruiseSpeedKmh);
        map.put("practicalRangeKm", practicalRangeKm);
        map.put("windWaveCapability", windWaveCapability == null ? null : windWaveCapability.name());
        return map;
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
