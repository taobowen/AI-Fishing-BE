package com.aifishing.planning.environment;

import com.aifishing.planning.PlanningProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SolarInfluenceTest {

    @Test
    void overcastCollapsesSolarStrength() {
        PlanningProperties.Environment.Solar config = new PlanningProperties.Environment.Solar();
        SolarPosition sun = new SolarPosition(180, 45, true);
        WeatherSample clear = sample(15, 600);
        WeatherSample overcast = sample(95, 40);
        WeatherSample partial = sample(50, 250);
        double clearStrength = SolarInfluence.compute(sun, clear, config).strength();
        double overcastStrength = SolarInfluence.compute(sun, overcast, config).strength();
        double partialStrength = SolarInfluence.compute(sun, partial, config).strength();
        assertThat(clearStrength).isGreaterThan(0.5);
        assertThat(overcastStrength).isLessThan(0.15);
        assertThat(partialStrength).isBetween(overcastStrength, clearStrength);
    }

    @Test
    void oppositeOrientationDifferenceCollapsesUnderOvercast() {
        PlanningProperties.Environment.Solar config = new PlanningProperties.Environment.Solar();
        SolarPosition sun = new SolarPosition(270, 40, true);
        SolarInfluence overcast = SolarInfluence.compute(sun, sample(95, 30), config);
        LocalOrientation westFacing = new LocalOrientation(270.0, null, null, false, OrientationConfidence.HIGH, "TEST");
        LocalOrientation eastFacing = new LocalOrientation(90.0, null, null, false, OrientationConfidence.HIGH, "TEST");
        double west = SolarExposure.orientationExposure(sun, westFacing, overcast);
        double east = SolarExposure.orientationExposure(sun, eastFacing, overcast);
        assertThat(Math.abs(west - east)).isLessThan(0.15);

        SolarInfluence clear = SolarInfluence.compute(sun, sample(10, 700), config);
        double westClear = SolarExposure.orientationExposure(sun, westFacing, clear);
        double eastClear = SolarExposure.orientationExposure(sun, eastFacing, clear);
        assertThat(westClear - eastClear).isGreaterThan(0.3);
    }

    private static WeatherSample sample(double cloud, double radiation) {
        return new WeatherSample(Instant.parse("2026-09-12T16:00:00Z"), 10.0, 270.0, 90.0, cloud, 16.0, 0.0, 1013.0, radiation, radiation);
    }
}
