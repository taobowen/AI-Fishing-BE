package com.aifishing.lake.processing.extract;

import java.util.LinkedHashMap;
import java.util.Map;

public record FeatureEvidence(
        double prominence,
        boolean bathymetrySupported,
        boolean geometryConsistent,
        Double interpolationDistanceM,
        int supportingDataTypes
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("prominence", prominence);
        map.put("bathymetrySupported", bathymetrySupported);
        map.put("geometryConsistent", geometryConsistent);
        if (interpolationDistanceM != null) {
            map.put("interpolationDistanceM", interpolationDistanceM);
        }
        map.put("supportingDataTypes", supportingDataTypes);
        return map;
    }
}
