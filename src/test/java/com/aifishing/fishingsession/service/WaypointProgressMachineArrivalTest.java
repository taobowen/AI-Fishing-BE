package com.aifishing.fishingsession.service;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.domain.WaypointSkipReason;
import com.aifishing.planning.domain.TripWaypoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaypointProgressMachineArrivalTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void confirmedArrivalEntersFishingWithoutDwellWait() {
        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        UUID waypointId = UUID.randomUUID();
        TripWaypoint planned = new TripWaypoint();
        planned.setId(waypointId);
        planned.setLocation(point(44.75, -78.92));
        planned.setEntryPoint(point(44.75, -78.92));
        SessionWaypointProgress row = navigating(waypointId);
        Instant t0 = Instant.parse("2026-09-08T17:00:00Z");
        machine.applyAcceptedHistory(List.of(row), Map.of(waypointId, planned), samples(t0, 44.75, -78.92));
        assertThat(row.getStatus()).isEqualTo(WaypointProgressStatus.FISHING);
        assertThat(row.getArrivedAt()).isNotNull();
    }

    @Test
    void fishingDoesNotReturnToNavigatingWhenGpsJittersAway() {
        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        UUID waypointId = UUID.randomUUID();
        TripWaypoint planned = new TripWaypoint();
        planned.setId(waypointId);
        planned.setLocation(point(44.75, -78.92));
        planned.setEntryPoint(point(44.75, -78.92));
        SessionWaypointProgress row = navigating(waypointId);
        row.setStatus(WaypointProgressStatus.FISHING);
        row.setArrivedAt(Instant.parse("2026-09-08T17:00:00Z"));
        Instant t0 = Instant.parse("2026-09-08T17:05:00Z");
        List<SessionLocationPoint> points = new ArrayList<>(samples(t0, 44.75, -78.92));
        points.add(pointRow("jitter", t0.plusSeconds(40), 44.751, -78.921));
        machine.applyAcceptedHistory(List.of(row), Map.of(waypointId, planned), points);
        assertThat(row.getStatus()).isNotEqualTo(WaypointProgressStatus.NAVIGATING);
    }

    @Test
    void manualSkipWritesUserReason() {
        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        SessionWaypointProgress current = navigating(UUID.randomUUID());
        SessionWaypointProgress next = upcoming(UUID.randomUUID(), 2);
        machine.manualSkip(current, Instant.parse("2026-09-08T17:00:00Z"), List.of(current, next));
        assertThat(current.getStatus()).isEqualTo(WaypointProgressStatus.SKIPPED);
        assertThat(current.getSkipReason()).isEqualTo(WaypointSkipReason.USER);
        assertThat(next.getStatus()).isEqualTo(WaypointProgressStatus.NAVIGATING);
    }

    @Test
    void completingFirstAbacStopDoesNotCompleteLaterZoneA() {
        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        SessionWaypointProgress firstA = navigating(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"));
        SessionWaypointProgress pointB = upcoming(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001"), 2);
        SessionWaypointProgress secondA = upcoming(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002"), 3);
        SessionWaypointProgress pointC = upcoming(UUID.fromString("cccccccc-cccc-cccc-cccc-000000000001"), 4);
        machine.manualComplete(firstA, Instant.parse("2026-09-08T17:00:00Z"), List.of(firstA, pointB, secondA, pointC));
        assertThat(firstA.getStatus()).isEqualTo(WaypointProgressStatus.COMPLETED);
        assertThat(pointB.getStatus()).isEqualTo(WaypointProgressStatus.NAVIGATING);
        assertThat(secondA.getStatus()).isEqualTo(WaypointProgressStatus.UPCOMING);
        assertThat(pointC.getStatus()).isEqualTo(WaypointProgressStatus.UPCOMING);
    }

    @Test
    void arrivingAtGuidanceTargetMarksThatWaypointWithoutSkippingPrior() {
        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        UUID spot2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
        UUID spot3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
        TripWaypoint planned2 = planned(spot2, 44.75, -78.92);
        TripWaypoint planned3 = planned(spot3, 44.76, -78.92);
        SessionWaypointProgress row2 = navigating(spot2);
        row2.setSequence(2);
        SessionWaypointProgress row3 = upcoming(spot3, 3);
        Instant t0 = Instant.parse("2026-09-08T17:00:00Z");

        machine.applyAcceptedHistory(
                List.of(row2, row3),
                Map.of(spot2, planned2, spot3, planned3),
                samples(t0, 44.76, -78.92),
                spot3
        );

        assertThat(row3.getStatus()).isEqualTo(WaypointProgressStatus.FISHING);
        assertThat(row3.getArrivedAt()).isNotNull();
        assertThat(row2.getStatus()).isEqualTo(WaypointProgressStatus.NAVIGATING);
        assertThat(row2.getArrivedAt()).isNull();
        assertThat(row2.getSkippedAt()).isNull();
        assertThat(row2.getCompletedAt()).isNull();
    }

    private static TripWaypoint planned(UUID id, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setLocation(point(lat, lng));
        waypoint.setEntryPoint(point(lat, lng));
        return waypoint;
    }

    private static SessionWaypointProgress navigating(UUID waypointId) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setTripWaypointId(waypointId);
        row.setSequence(1);
        row.setStatus(WaypointProgressStatus.NAVIGATING);
        return row;
    }

    private static SessionWaypointProgress upcoming(UUID waypointId, int sequence) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setTripWaypointId(waypointId);
        row.setSequence(sequence);
        row.setStatus(WaypointProgressStatus.UPCOMING);
        return row;
    }

    private static List<SessionLocationPoint> samples(Instant t0, double lat, double lng) {
        return List.of(
                pointRow("a", t0, lat, lng),
                pointRow("b", t0.plusSeconds(10), lat, lng),
                pointRow("c", t0.plusSeconds(20), lat, lng),
                pointRow("d", t0.plusSeconds(30), lat, lng)
        );
    }

    private static SessionLocationPoint pointRow(String id, Instant at, double lat, double lng) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setId(UUID.randomUUID());
        point.setClientPointId(id);
        point.setRecordedAt(at);
        point.setQuality(LocationQuality.ACCEPTED);
        point.setLocation(point(lat, lng));
        return point;
    }

    private static Point point(double lat, double lng) {
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }
}
