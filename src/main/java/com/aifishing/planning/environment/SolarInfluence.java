package com.aifishing.planning.environment;

import com.aifishing.planning.PlanningProperties;

public record SolarInfluence(
        double strength,
        String source,
        OrientationConfidence confidence
) {
    public static SolarInfluence none(String source) {
        return new SolarInfluence(0, source, OrientationConfidence.HIGH);
    }

    public static SolarInfluence compute(
            SolarPosition sun,
            WeatherSample weather,
            PlanningProperties.Environment.Solar config
    ) {
        if (sun == null || !sun.sunUp() || sun.elevationDeg() <= 0) {
            return none("NIGHT");
        }
        double elevationFactor = Math.max(0, Math.min(1, Math.sin(Math.toRadians(sun.elevationDeg()))));
        Double direct = weather == null ? null : weather.directRadiation();
        Double shortwave = weather == null ? null : weather.shortwaveRadiation();
        Double radiation = direct != null ? direct : shortwave;
        if (radiation != null) {
            double low = config.getLowRadiationThreshold();
            double high = Math.max(low + 1, config.getHighRadiationThreshold());
            double strength = clamp((radiation - low) / (high - low)) * elevationFactor;
            OrientationConfidence confidence = direct != null ? OrientationConfidence.HIGH : OrientationConfidence.MEDIUM;
            return new SolarInfluence(strength, direct != null ? "RADIATION_DIRECT" : "RADIATION_SHORTWAVE", confidence);
        }
        Double cloud = weather == null ? null : weather.cloudCoverPercent();
        if (cloud != null) {
            double overcast = config.getOvercastCloudPercent();
            double clear = config.getClearCloudPercent();
            double span = Math.max(1, overcast - clear);
            double clearness = clamp((overcast - cloud) / span);
            if (cloud >= overcast) {
                clearness = 0;
            }
            return new SolarInfluence(clearness * elevationFactor, "CLOUD_ELEVATION", OrientationConfidence.MEDIUM);
        }
        return new SolarInfluence(0.5 * elevationFactor, "ELEVATION_ONLY", OrientationConfidence.LOW);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
