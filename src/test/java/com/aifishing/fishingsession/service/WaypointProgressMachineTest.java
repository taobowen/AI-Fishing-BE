package com.aifishing.fishingsession.service;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaypointProgressMachineTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void twoSparseSamplesDoNotConfirmArrival() {
        Instant t0 = Instant.parse("2026-09-02T16:00:00Z");
        List<SessionLocationPoint> window = List.of(
                point("a", t0),
                point("b", t0.plusSeconds(30))
        );
        assertThat(WaypointProgressMachine.arrivalConfirmed(window, 30, 4)).isFalse();
    }

    @Test
    void fourSamplesOverConfirmSecondsArrive() {
        Instant t0 = Instant.parse("2026-09-02T16:00:00Z");
        List<SessionLocationPoint> window = List.of(
                point("a", t0),
                point("b", t0.plusSeconds(10)),
                point("c", t0.plusSeconds(20)),
                point("d", t0.plusSeconds(30))
        );
        assertThat(WaypointProgressMachine.arrivalConfirmed(window, 30, 4)).isTrue();
    }

    private static SessionLocationPoint point(String clientId, Instant recordedAt) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setId(UUID.randomUUID());
        point.setClientPointId(clientId);
        point.setRecordedAt(recordedAt);
        point.setQuality(LocationQuality.ACCEPTED);
        Point geometry = FACTORY.createPoint(new Coordinate(-78.92, 44.75));
        geometry.setSRID(GeoMapper.SRID);
        point.setLocation(geometry);
        return point;
    }
}
