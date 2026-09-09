package com.aifishing.planning.environment;

import java.util.LinkedHashMap;
import java.util.Map;

public record EnvironmentSnapshot(
        Double sunAzimuth,
        Double sunElevation,
        Double shorelineWaterFacingAspect,
        Double slopeAspect,
        Double rawFeatureOrientation,
        String orientationConfidence,
        String orientationSource,
        Double solarInfluenceStrength,
        String solarInfluenceSource,
        Double windFromAzimuth,
        Double windFlowAzimuth,
        Double windSpeedKmh,
        Double airTemperatureC,
        Double cloudCoverPercent,
        Double directRadiation,
        Double shortwaveRadiation,
        String lightPreference,
        String temperatureSource
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "sunAzimuth", sunAzimuth);
        put(map, "sunElevation", sunElevation);
        put(map, "shorelineWaterFacingAspect", shorelineWaterFacingAspect);
        put(map, "slopeAspect", slopeAspect);
        put(map, "rawFeatureOrientation", rawFeatureOrientation);
        put(map, "orientationConfidence", orientationConfidence);
        put(map, "orientationSource", orientationSource);
        put(map, "solarInfluenceStrength", solarInfluenceStrength);
        put(map, "solarInfluenceSource", solarInfluenceSource);
        put(map, "windFromAzimuth", windFromAzimuth);
        put(map, "windFlowAzimuth", windFlowAzimuth);
        put(map, "windSpeedKmh", windSpeedKmh);
        put(map, "airTemperatureC", airTemperatureC);
        put(map, "cloudCoverPercent", cloudCoverPercent);
        put(map, "directRadiation", directRadiation);
        put(map, "shortwaveRadiation", shortwaveRadiation);
        put(map, "lightPreference", lightPreference);
        put(map, "temperatureSource", temperatureSource);
        map.put("azimuthConvention", AzimuthConvention.DESCRIPTION);
        map.put("rawOrientationUsedAsFacing", false);
        return map;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
