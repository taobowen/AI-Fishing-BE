package com.aifishing.planning.environment;

import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.SPA;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class SolarPositionService {

    public SolarPosition at(Instant instant, ZoneId zone, Point location) {
        if (instant == null || location == null) {
            return new SolarPosition(Double.NaN, Double.NaN, false);
        }
        ZoneId resolved = zone == null ? ZoneId.of("UTC") : zone;
        ZonedDateTime zoned = instant.atZone(resolved);
        double deltaT = DeltaT.estimate(zoned.toLocalDate());
        net.e175.klaus.solarpositioning.SolarPosition spa = SPA.calculateSolarPosition(
                zoned,
                location.getY(),
                location.getX(),
                0,
                deltaT
        );
        double azimuth = AzimuthConvention.normalize(spa.azimuth());
        double elevation = 90.0 - spa.zenithAngle();
        return new SolarPosition(azimuth, elevation, elevation > 0);
    }
}
