package com.aifishing.fishingsession.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.feedback.effort.repo.SessionPauseIntervalRepository;
import com.aifishing.feedback.effort.service.FishingEffortService;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.dto.FishingSessionResponse;
import com.aifishing.fishingsession.dto.LocationBatchRequest;
import com.aifishing.fishingsession.dto.LocationPointRequest;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionClientEventRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.empirical.EmpiricalAggregationService;
import com.aifishing.guidance.events.ActivityStateUpdater;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.TransitLegMaterializer;
import com.aifishing.planning.spatial.repo.TripPlanTransitLegRepository;
import com.aifishing.trip.repo.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FishingSessionAdHocProgressTest {

    private static final Instant NOW = Instant.parse("2026-09-17T18:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SPOT_2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Mock
    private CurrentUser currentUser;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripPlanRepository tripPlanRepository;
    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionWaypointProgressRepository progressRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;
    @Mock
    private SessionClientEventRepository clientEventRepository;
    @Mock
    private SessionPauseIntervalRepository pauseIntervalRepository;
    @Mock
    private FishingEffortService fishingEffortService;
    @Mock
    private EmpiricalPerformanceService empiricalPerformanceService;
    @Mock
    private WaypointProgressMachine waypointMachine;
    @Mock
    private SessionMapper mapper;
    @Mock
    private TransitLegMaterializer transitLegMaterializer;
    @Mock
    private TripPlanTransitLegRepository transitLegRepository;
    @Mock
    private SessionEventWriter sessionEventWriter;
    @Mock
    private ActivityStateUpdater activityStateUpdater;
    @Mock
    private LiveWaypointActivityStore livePositionStore;
    @Mock
    private EmpiricalAggregationService empiricalAggregationService;
    @Mock
    private AdHocFishingService adHocFishingService;
    @Mock
    private com.aifishing.guidance.horizon.ActiveGuidanceTarget activeGuidanceTarget;

    private final List<SessionLocationPoint> stored = new ArrayList<>();
    private FishingSessionService service;

    @BeforeEach
    void setUp() {
        service = new FishingSessionService(
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SessionProperties(),
                new GeoMapper(),
                tripRepository,
                tripPlanRepository,
                tripWaypointRepository,
                sessionRepository,
                progressRepository,
                locationPointRepository,
                clientEventRepository,
                pauseIntervalRepository,
                fishingEffortService,
                empiricalPerformanceService,
                waypointMachine,
                mapper,
                transitLegMaterializer,
                transitLegRepository,
                sessionEventWriter,
                activityStateUpdater,
                livePositionStore,
                empiricalAggregationService,
                adHocFishingService,
                activeGuidanceTarget
        );
    }

    @Test
    void openAdHocStopDoesNotCompleteOrSkipOriginalSpot2Progress() {
        FishingSession session = session();
        SessionWaypointProgress spot2 = navigatingSpot2();
        when(currentUser.id()).thenReturn(USER_ID);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        when(locationPointRepository.findExistingClientPointIds(eq(SESSION_ID), any())).thenReturn(Set.of());
        when(locationPointRepository.findFirstByFishingSessionIdOrderByRecordedAtDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        when(locationPointRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<SessionLocationPoint> points = invocation.getArgument(0);
            points.forEach(stored::add);
            return stored;
        });
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(spot2));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(spot2Waypoint()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED
        )).thenAnswer(invocation -> List.copyOf(stored));
        when(adHocFishingService.hasOpenStop(SESSION_ID)).thenReturn(true);
        when(tripPlanRepository.findById(PLAN_ID)).thenReturn(Optional.empty());
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
        when(mapper.session(any(), any(), any(), any())).thenReturn(dummyResponse(session, List.of(spot2)));

        service.ingestLocations(SESSION_ID, new LocationBatchRequest(List.of(
                new LocationPointRequest("p1", NOW, new GeoPointDto(44.75, -79.35), 8.0, null, 0.1, null)
        )));

        verify(waypointMachine, never()).applyAcceptedHistory(any(), any(), any(), any());
        verify(adHocFishingService).maybeEndFromDeparture(eq(session), any());
        assertThat(spot2.getStatus()).isEqualTo(WaypointProgressStatus.NAVIGATING);
        assertThat(spot2.getCompletedAt()).isNull();
        assertThat(spot2.getSkippedAt()).isNull();
        verify(tripWaypointRepository, never()).save(any());
        verify(tripPlanRepository, never()).save(any());
    }

    private static FishingSession session() {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTripId(TRIP_ID);
        session.setTripPlanId(PLAN_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setStartedAt(NOW.minusSeconds(600));
        return session;
    }

    private static SessionWaypointProgress navigatingSpot2() {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        row.setFishingSessionId(SESSION_ID);
        row.setTripWaypointId(SPOT_2);
        row.setSequence(2);
        row.setStatus(WaypointProgressStatus.NAVIGATING);
        return row;
    }

    private static TripWaypoint spot2Waypoint() {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(SPOT_2);
        waypoint.setTripPlanId(PLAN_ID);
        waypoint.setSequence(2);
        Point geometry = FACTORY.createPoint(new Coordinate(-79.35, 44.75));
        geometry.setSRID(GeoMapper.SRID);
        waypoint.setLocation(geometry);
        return waypoint;
    }

    private static FishingSessionResponse dummyResponse(FishingSession session, List<SessionWaypointProgress> progress) {
        return new FishingSessionResponse(
                session.getId(),
                session.getTripId(),
                session.getUserId(),
                session.getTripPlanId(),
                session.getPlanVersion(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPausedAt(),
                session.getTotalPausedSeconds(),
                session.getSummary(),
                List.of(),
                List.of(),
                false,
                null,
                null,
                session.getActivityState(),
                session.getActivityStateSource(),
                null,
                false,
                null,
                session.getGuidanceMode()
        );
    }
}

