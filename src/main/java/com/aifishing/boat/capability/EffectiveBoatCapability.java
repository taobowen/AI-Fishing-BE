package com.aifishing.boat.capability;

import com.aifishing.common.enums.WindWaveCapability;

import java.util.LinkedHashMap;
import java.util.Map;

public record EffectiveBoatCapability(
        double cruiseSpeedKmh,
        Double estimatedPracticalRangeKm,
        Double systemUsableRangeKm,
        Double comfortableCapKm,
        Double effectiveUsableRangeKm,
        boolean rangeEnforced,
        double maxLegKm,
        WindWaveCapability windWaveCapability,
        double windDerateFraction,
        Map<String, Object> weatherAdjustment
) {
    public EffectiveBoatCapability {
        weatherAdjustment = weatherAdjustment == null ? Map.of() : Map.copyOf(weatherAdjustment);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cruiseSpeedKmh", cruiseSpeedKmh);
        map.put("estimatedPracticalRangeKm", estimatedPracticalRangeKm);
        map.put("systemUsableRangeKm", systemUsableRangeKm);
        map.put("comfortableCapKm", comfortableCapKm);
        map.put("effectiveUsableRangeKm", effectiveUsableRangeKm);
        map.put("rangeEnforced", rangeEnforced);
        map.put("maxLegKm", maxLegKm);
        map.put("windWaveCapability", windWaveCapability == null ? null : windWaveCapability.name());
        map.put("windDerateFraction", windDerateFraction);
        map.put("weatherAdjustment", weatherAdjustment);
        return map;
    }
}
