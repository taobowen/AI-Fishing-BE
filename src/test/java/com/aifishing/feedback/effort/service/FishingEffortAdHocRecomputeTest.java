package com.aifishing.feedback.effort.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.domain.SessionPauseInterval;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.feedback.effort.repo.SessionPauseIntervalRepository;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FishingEffortAdHocRecomputeTest {

    private static final Instant START = Instant.parse("2026-09-17T10:20:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SPOT_2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;
    @Mock
    private SessionWaypointProgressRepository progressRepository;
    @Mock
    private SessionAdHocFishingStopRepository adHocStopRepository;
    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private SessionPauseIntervalRepository pauseIntervalRepository;
    @Mock
    private FishingEffortSegmentRepository segmentRepository;
    @Mock
    private ActiveGuidanceTarget activeGuidanceTarget;

    private FishingEffortService service;

    @BeforeEach
    void setUp() {
        service = new FishingEffortService(
                sessionRepository,
                locationPointRepository,
                progressRepository,
                adHocStopRepository,
                tripWaypointRepository,
                pauseIntervalRepository,
                segmentRepository,
                new SessionProperties(),
                new FeedbackProperties(),
                new GeoMapper(),
                activeGuidanceTarget
        );
    }

    @Test
    void navigatingGpsDuringAdHocIsFishingEffortNotTiedToSpot2() {
        stubSessionAndTrack(List.of());
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID))
                .thenReturn(List.of(stop(START, START.plusSeconds(20 * 60))));

        service.recompute(SESSION_ID);

        ArgumentCaptor<List<FishingEffortSegment>> saved = ArgumentCaptor.forClass(List.class);
        verify(segmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty();
        assertThat(saved.getValue()).allMatch(segment -> segment.getSegmentType() == EffortSegmentType.FISHING);
        assertThat(saved.getValue()).allMatch(segment -> segment.getTripWaypointId() == null);
    }

    @Test
    void pauseDuringAdHocExcludesEffort() {
        stubSessionAndTrack(List.of(pause(START.plusSeconds(5), START.plusSeconds(90))));
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID))
                .thenReturn(List.of(stop(START, START.plusSeconds(20 * 60))));

        service.recompute(SESSION_ID);

        verify(segmentRepository).deleteByFishingSessionId(SESSION_ID);
        verify(segmentRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void gpsAtGuidanceTargetIsFishingEffortOnThatWaypoint() {
        UUID spot3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
        stubSessionAndTrack(List.of());
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID)).thenReturn(List.of());
        when(activeGuidanceTarget.resolve(org.mockito.ArgumentMatchers.any(FishingSession.class))).thenReturn(spot3);
        TripWaypoint target = new TripWaypoint();
        target.setId(spot3);
        target.setTripPlanId(PLAN_ID);
        target.setLocation(point(44.75, -79.35));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID))
                .thenReturn(List.of(spot2Far(), target));
        SessionWaypointProgress navigating = new SessionWaypointProgress();
        navigating.setTripWaypointId(SPOT_2);
        navigating.setSequence(2);
        navigating.setStatus(WaypointProgressStatus.NAVIGATING);
        SessionWaypointProgress upcoming = new SessionWaypointProgress();
        upcoming.setTripWaypointId(spot3);
        upcoming.setSequence(3);
        upcoming.setStatus(WaypointProgressStatus.UPCOMING);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID))
                .thenReturn(List.of(navigating, upcoming));

        service.recompute(SESSION_ID);

        ArgumentCaptor<List<FishingEffortSegment>> saved = ArgumentCaptor.forClass(List.class);
        verify(segmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty();
        assertThat(saved.getValue()).anyMatch(segment ->
                segment.getSegmentType() == EffortSegmentType.FISHING
                        && spot3.equals(segment.getTripWaypointId()));
    }

    private static TripWaypoint spot2Far() {
        TripWaypoint spot2 = new TripWaypoint();
        spot2.setId(SPOT_2);
        spot2.setTripPlanId(PLAN_ID);
        spot2.setLocation(point(44.80, -79.40));
        return spot2;
    }

    private void stubSessionAndTrack(List<SessionPauseInterval> pauses) {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setTripPlanId(PLAN_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setStartedAt(START);
        session.setEndedAt(START.plusSeconds(20 * 60));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED
        )).thenReturn(List.of(
                gps("a", START),
                gps("b", START.plusSeconds(60))
        ));
        SessionWaypointProgress navigating = new SessionWaypointProgress();
        navigating.setTripWaypointId(SPOT_2);
        navigating.setSequence(2);
        navigating.setStatus(WaypointProgressStatus.NAVIGATING);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID))
                .thenReturn(List.of(navigating));
        TripWaypoint spot2 = new TripWaypoint();
        spot2.setId(SPOT_2);
        spot2.setTripPlanId(PLAN_ID);
        spot2.setLocation(point(44.80, -79.40));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(spot2));
        when(pauseIntervalRepository.findByFishingSessionIdOrderByPausedAtAsc(SESSION_ID)).thenReturn(pauses);
    }

    private static SessionAdHocFishingStop stop(Instant startedAt, Instant endedAt) {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setStartedAt(startedAt);
        stop.setEndedAt(endedAt);
        stop.setLocation(point(44.75, -79.35));
        return stop;
    }

    private static SessionPauseInterval pause(Instant pausedAt, Instant resumedAt) {
        SessionPauseInterval interval = new SessionPauseInterval();
        interval.setPausedAt(pausedAt);
        interval.setResumedAt(resumedAt);
        return interval;
    }

    private static SessionLocationPoint gps(String id, Instant at) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setClientPointId(id);
        point.setRecordedAt(at);
        point.setQuality(LocationQuality.ACCEPTED);
        point.setLocation(point(44.75, -79.35));
        return point;
    }

    private static Point point(double lat, double lng) {
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }
}
