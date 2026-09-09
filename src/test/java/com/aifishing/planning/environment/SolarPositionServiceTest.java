package com.aifishing.planning.environment;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SolarPositionServiceTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private final SolarPositionService service = new SolarPositionService();
    private final Point head = point(-78.92, 44.75);

    @Test
    void noonIsSouthAndNightIsDown() {
        Instant noon = ZonedDateTime.of(2026, 9, 12, 13, 0, 0, 0, TORONTO).toInstant();
        SolarPosition midday = service.at(noon, TORONTO, head);
        assertThat(midday.sunUp()).isTrue();
        assertThat(midday.elevationDeg()).isGreaterThan(30);
        assertThat(AzimuthConvention.circularDelta(midday.azimuthDeg(), 180)).isLessThan(40);

        Instant night = ZonedDateTime.of(2026, 9, 12, 23, 30, 0, 0, TORONTO).toInstant();
        SolarPosition afterDark = service.at(night, TORONTO, head);
        assertThat(afterDark.sunUp()).isFalse();
        assertThat(afterDark.elevationDeg()).isLessThan(0);
    }

    @Test
    void morningEastAfternoonWestAndDstInstant() {
        Instant morning = ZonedDateTime.of(2026, 9, 12, 8, 0, 0, 0, TORONTO).toInstant();
        Instant afternoon = ZonedDateTime.of(2026, 9, 12, 16, 0, 0, 0, TORONTO).toInstant();
        SolarPosition am = service.at(morning, TORONTO, head);
        SolarPosition pm = service.at(afternoon, TORONTO, head);
        assertThat(am.azimuthDeg()).isGreaterThan(70).isLessThan(140);
        assertThat(pm.azimuthDeg()).isGreaterThan(220).isLessThan(290);

        Instant dst = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, TORONTO).toInstant();
        SolarPosition july = service.at(dst, TORONTO, head);
        assertThat(july.sunUp()).isTrue();
        assertThat(july.elevationDeg()).isGreaterThan(50);

        Instant midnight = LocalDate.of(2026, 9, 13).atTime(LocalTime.MIDNIGHT).atZone(TORONTO).toInstant();
        assertThat(service.at(midnight, TORONTO, head).sunUp()).isFalse();
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }
}
