package com.aifishing.feedback.catchlog.service;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.planning.domain.TripWaypoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatchAssociationServiceTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private static final Instant STARTED = Instant.parse("2026-09-17T16:00:00Z");
    private static final UUID FIRST_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID SECOND_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final UUID POINT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");
    private static final double LAT = 44.75;
    private static final double LNG = -79.35;

    private final CatchAssociationService service =
            new CatchAssociationService(null, null, null, new FeedbackProperties(), null);

    @Test
    void nearestPrefersCurrentFishingOverCompletedColocatedStop() {
        TripWaypoint completedA = waypoint(FIRST_A, 0);
        TripWaypoint fishingA = waypoint(SECOND_A, 1);

        CatchAssociationService.Association association = service.associate(
                session(),
                null,
                STARTED.plusSeconds(600),
                point(LAT, LNG),
                List.of(
                        progress(FIRST_A, 0, WaypointProgressStatus.COMPLETED),
                        progress(SECOND_A, 1, WaypointProgressStatus.FISHING)
                ),
                List.of(completedA, fishingA)
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.NEAREST_WAYPOINT);
        assertThat(association.tripWaypointId()).isEqualTo(SECOND_A);
    }

    @Test
    void nearestPrefersUpcomingColocatedStopOverCompletedWhenCurrentIsElsewhere() {
        TripWaypoint completedA = waypoint(FIRST_A, 0);
        TripWaypoint upcomingA = waypoint(SECOND_A, 1);
        TripWaypoint currentB = waypoint(POINT_B, 2);
        currentB.setLocation(point(LAT + 0.05, LNG));

        CatchAssociationService.Association association = service.associate(
                session(),
                null,
                STARTED.plusSeconds(600),
                point(LAT, LNG),
                List.of(
                        progress(FIRST_A, 0, WaypointProgressStatus.COMPLETED),
                        progress(SECOND_A, 1, WaypointProgressStatus.UPCOMING),
                        progress(POINT_B, 2, WaypointProgressStatus.NAVIGATING)
                ),
                List.of(completedA, upcomingA, currentB)
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.NEAREST_WAYPOINT);
        assertThat(association.tripWaypointId()).isEqualTo(SECOND_A);
    }

    @Test
    void clientTripWaypointIdStillBindsEvenWhenAnotherColocatedStopIsActive() {
        CatchAssociationService.Association association = service.associate(
                session(),
                FIRST_A,
                STARTED.plusSeconds(60),
                point(LAT, LNG),
                List.of(
                        progress(FIRST_A, 0, WaypointProgressStatus.NAVIGATING),
                        progress(SECOND_A, 1, WaypointProgressStatus.UPCOMING)
                ),
                List.of(waypoint(FIRST_A, 0), waypoint(SECOND_A, 1))
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.CURRENT_WAYPOINT);
        assertThat(association.tripWaypointId()).isEqualTo(FIRST_A);
    }

    @Test
    void omittedWaypointDuringAdHocStopAssociatesToStop() {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setId(UUID.fromString("cccccccc-cccc-cccc-cccc-000000000001"));
        stop.setStartedAt(STARTED.plusSeconds(60));
        stop.setLocation(point(LAT + 0.01, LNG));
        stop.setFishingTargetId(UUID.fromString("dddddddd-dddd-dddd-dddd-000000000001"));

        CatchAssociationService.Association association = service.associate(
                session(),
                null,
                STARTED.plusSeconds(120),
                point(LAT + 0.01, LNG),
                List.of(progress(FIRST_A, 0, WaypointProgressStatus.NAVIGATING)),
                List.of(waypoint(FIRST_A, 0)),
                List.of(stop)
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.AD_HOC_STOP);
        assertThat(association.adHocFishingStopId()).isEqualTo(stop.getId());
        assertThat(association.tripWaypointId()).isNull();
        assertThat(association.fishingTargetId()).isEqualTo(stop.getFishingTargetId());
    }

    @Test
    void explicitWaypointIdUnchangedDuringAdHocStop() {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setId(UUID.fromString("cccccccc-cccc-cccc-cccc-000000000002"));
        stop.setStartedAt(STARTED);

        CatchAssociationService.Association association = service.associate(
                session(),
                FIRST_A,
                STARTED.plusSeconds(60),
                point(LAT, LNG),
                List.of(progress(FIRST_A, 0, WaypointProgressStatus.NAVIGATING)),
                List.of(waypoint(FIRST_A, 0)),
                List.of(stop)
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.CURRENT_WAYPOINT);
        assertThat(association.tripWaypointId()).isEqualTo(FIRST_A);
        assertThat(association.adHocFishingStopId()).isNull();
    }

    @Test
    void gpsAtGuidanceTargetPrefersThatWaypointOverOriginalNavigating() {
        ActiveGuidanceTarget target = org.mockito.Mockito.mock(ActiveGuidanceTarget.class);
        CatchAssociationService preferring = new CatchAssociationService(
                null, null, null, new FeedbackProperties(), target);
        FishingSession session = session();
        org.mockito.Mockito.when(target.resolve(session)).thenReturn(SECOND_A);
        TripWaypoint navigating = waypoint(FIRST_A, 0);
        navigating.setLocation(point(LAT + 0.05, LNG));
        TripWaypoint upcoming = waypoint(SECOND_A, 1);

        CatchAssociationService.Association association = preferring.associate(
                session,
                null,
                STARTED.plusSeconds(60),
                point(LAT, LNG),
                List.of(
                        progress(FIRST_A, 0, WaypointProgressStatus.NAVIGATING),
                        progress(SECOND_A, 1, WaypointProgressStatus.UPCOMING)
                ),
                List.of(navigating, upcoming)
        );

        assertThat(association.method()).isEqualTo(CatchAssociationMethod.NEAREST_WAYPOINT);
        assertThat(association.tripWaypointId()).isEqualTo(SECOND_A);
    }

    private static FishingSession session() {
        FishingSession session = new FishingSession();
        session.setStartedAt(STARTED);
        return session;
    }

    private static TripWaypoint waypoint(UUID id, int sequence) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setSequence(sequence);
        waypoint.setLocation(point(LAT, LNG));
        return waypoint;
    }

    private static SessionWaypointProgress progress(UUID tripWaypointId, int sequence, WaypointProgressStatus status) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setTripWaypointId(tripWaypointId);
        row.setSequence(sequence);
        row.setStatus(status);
        return row;
    }

    private static Point point(double lat, double lng) {
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }
}
