package com.aifishing.guidance.state;

import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.strategy.weather.WeatherService;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryFishingSessionStateBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LAKE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PLAN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TRIP_WP = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PROGRESS_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID ZONE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID MEMBER_A = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID MEMBER_B = UUID.fromString("99999999-9999-9999-9999-999999999992");

    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private LakeRepository lakeRepository;
    @Mock
    private BoatRepository boatRepository;
    @Mock
    private SessionWaypointProgressRepository progressRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;
    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private CatchEventRepository catchEventRepository;
    @Mock
    private EmpiricalPerformanceService empiricalPerformanceService;
    @Mock
    private LureEventRepository lureEventRepository;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private GuidancePlanVersionRepository planVersionRepository;
    @Mock
    private GuidancePlanStepRepository planStepRepository;
    @Mock
    private SessionAdHocFishingStopRepository adHocStopRepository;
    @Mock
    private AgentDeliveredDecisionRepository deliveredDecisionRepository;

    private RepositoryFishingSessionStateBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RepositoryFishingSessionStateBuilder(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PlanningProperties(),
                sessionRepository,
                tripRepository,
                lakeRepository,
                boatRepository,
                progressRepository,
                locationPointRepository,
                tripWaypointRepository,
                catchEventRepository,
                empiricalPerformanceService,
                lureEventRepository,
                sessionEventRepository,
                planVersionRepository,
                planStepRepository,
                adHocStopRepository,
                deliveredDecisionRepository,
                new OpportunityRevisitSupport(new GuidanceProperties(), new com.aifishing.fishingsession.SessionProperties(), Clock.fixed(NOW, ZoneOffset.UTC))
        );
        lenient().when(deliveredDecisionRepository.findByFishingSessionIdOrderByCreatedAtAsc(SESSION_ID))
                .thenReturn(List.of());
    }

    @Test
    void constructorDoesNotTakeWeatherService() {
        for (Constructor<?> constructor : RepositoryFishingSessionStateBuilder.class.getDeclaredConstructors()) {
            assertThat(Arrays.asList(constructor.getParameterTypes())).doesNotContain(WeatherService.class);
        }
    }

    @Test
    void buildCopiesEnvironmentAndKeepsDistinctWaypointIdsWithoutWrites() {
        FishingSession session = session();
        Trip trip = trip();
        Lake lake = lake();
        SessionWaypointProgress progress = progress();
        SessionLocationPoint gps = gps();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(progress));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(tripWaypoint()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());

        WeatherSnapshot weather = new WeatherSnapshot(
                GuidanceSchemaVersion.VALUE,
                NOW.minusSeconds(240),
                WeatherCondition.CLOUDY,
                16.0,
                CompassDirection.W,
                18.0,
                1012.0
        );
        EnvironmentSnapshot environment = new EnvironmentSnapshot(NOW, weather, false, 4, 44.75, -78.92);

        FishingSessionState state = builder.build(SESSION_ID, environment);

        assertThat(state.fishing().currentTripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(state.fishing().currentSessionWaypointProgressId()).isEqualTo(PROGRESS_ID);
        assertThat(state.fishing().currentTripWaypointId())
                .isNotEqualTo(state.fishing().currentSessionWaypointProgressId());
        assertThat(state.position().latitudeWgs84()).isEqualTo(44.75);
        assertThat(state.position().longitudeWgs84()).isEqualTo(-78.92);
        assertThat(state.environment().weather()).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(state.environment().windSpeedKph()).isEqualTo(16.0);
        assertThat(state.environment().weatherObservedAt()).isEqualTo(NOW.minusSeconds(240));
        assertThat(state.environment().weatherAgeMinutes()).isEqualTo(4);
        assertThat(state.recent().summaries()).isEmpty();
        assertThat(state.plan().originalPlanSteps()).hasSize(1);
        assertThat(state.plan().originalPlanSteps().getFirst().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(state.plan().originalPlanSteps().getFirst().physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.plan().originalPlanSteps().getFirst().packageMemberIds()).containsExactly(MEMBER_A, MEMBER_B);
        assertThat(state.plan().originalPlanSteps().getFirst().progressStatus()).isEqualTo("NAVIGATING");
        assertThat(state.plan().activeGuidanceTargetTripWaypointId()).isNull();

        verify(sessionRepository, never()).save(any());
        verify(progressRepository, never()).save(any());
        verify(progressRepository, never()).saveAll(any());
        verify(locationPointRepository, never()).save(any());
        verify(catchEventRepository, never()).save(any());
        verify(lureEventRepository, never()).save(any());
        verify(empiricalPerformanceService, never()).recompute(any());
    }

    @Test
    void guidanceVersionDoesNotReplaceOriginalPlanSteps() {
        UUID versionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID horizonOnly = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        FishingSession session = session();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake()));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(progress()));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(tripWaypoint()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps()));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        GuidancePlanVersionEntity version = new GuidancePlanVersionEntity();
        version.setId(versionId);
        version.setFishingSessionId(SESSION_ID);
        version.setVersion(2);
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.of(version));
        GuidancePlanStepEntity step = new GuidancePlanStepEntity();
        step.setStep(1);
        step.setType(GuidanceAction.MOVE);
        step.setCommitted(true);
        step.setTripWaypointId(horizonOnly);
        when(planStepRepository.findByGuidancePlanVersionIdOrderByStepAsc(versionId)).thenReturn(List.of(step));

        FishingSessionState state = builder.build(SESSION_ID, new EnvironmentSnapshot(
                NOW,
                new WeatherSnapshot(
                        GuidanceSchemaVersion.VALUE,
                        NOW.minusSeconds(240),
                        WeatherCondition.CLOUDY,
                        16.0,
                        CompassDirection.W,
                        18.0,
                        1012.0
                ),
                false,
                4,
                44.75,
                -78.92
        ));

        assertThat(state.plan().currentGuidancePlanVersion()).isEqualTo(2);
        assertThat(state.plan().shortHorizonSteps()).hasSize(1);
        assertThat(state.plan().shortHorizonSteps().getFirst().tripWaypointId()).isEqualTo(horizonOnly);
        assertThat(state.plan().originalPlanSteps()).hasSize(1);
        assertThat(state.plan().originalPlanSteps().getFirst().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(state.plan().originalPlanSteps().getFirst().progressStatus()).isEqualTo("NAVIGATING");
        assertThat(state.plan().activeGuidanceTargetTripWaypointId()).isEqualTo(horizonOnly);
    }

    @Test
    void originalPlanStepsKeepDisjointMembersForRepeatedZone() {
        UUID firstA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
        UUID pointB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");
        UUID secondA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
        UUID pointC = UUID.fromString("cccccccc-cccc-cccc-cccc-000000000001");
        UUID memberA1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID memberA2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        UUID memberA3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
        UUID memberA4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
        FishingSession session = session();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake()));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(
                progressAt(firstA, 1, WaypointProgressStatus.COMPLETED),
                progressAt(pointB, 2, WaypointProgressStatus.NAVIGATING),
                progressAt(secondA, 3, WaypointProgressStatus.UPCOMING),
                progressAt(pointC, 4, WaypointProgressStatus.UPCOMING)
        ));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(
                zoneWaypoint(firstA, 1, List.of(memberA1, memberA2)),
                pointWaypoint(pointB, 2),
                zoneWaypoint(secondA, 3, List.of(memberA3, memberA4)),
                pointWaypoint(pointC, 4)
        ));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps()));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());

        FishingSessionState state = builder.build(SESSION_ID, new EnvironmentSnapshot(
                NOW,
                new WeatherSnapshot(
                        GuidanceSchemaVersion.VALUE,
                        NOW.minusSeconds(240),
                        WeatherCondition.CLOUDY,
                        16.0,
                        CompassDirection.W,
                        18.0,
                        1012.0
                ),
                false,
                4,
                44.75,
                -78.92
        ));

        assertThat(state.plan().originalPlanSteps()).hasSize(4);
        assertThat(state.plan().originalPlanSteps().get(0).tripWaypointId()).isEqualTo(firstA);
        assertThat(state.plan().originalPlanSteps().get(2).tripWaypointId()).isEqualTo(secondA);
        assertThat(state.plan().originalPlanSteps().get(0).physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.plan().originalPlanSteps().get(2).physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.plan().originalPlanSteps().get(0).packageMemberIds()).containsExactly(memberA1, memberA2);
        assertThat(state.plan().originalPlanSteps().get(2).packageMemberIds()).containsExactly(memberA3, memberA4);
        assertThat(state.plan().originalPlanSteps().get(2).packageMemberIds()).doesNotContain(memberA1, memberA2);
        assertThat(state.plan().originalPlanSteps().get(0).progressStatus()).isEqualTo("COMPLETED");
        assertThat(state.plan().originalPlanSteps().get(2).progressStatus()).isEqualTo("UPCOMING");
        assertThat(state.opportunityRevisit().packageCooldowns()).isEmpty();
    }

    @Test
    void opportunityRevisitCoolsLeftVisitPackageNotWholeZoneAndRecordsDeliveredMoves() {
        UUID firstA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
        UUID pointB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");
        UUID secondA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
        UUID memberA1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID memberA2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        UUID memberA3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
        UUID memberA4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
        FishingSession session = session();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake()));
        SessionWaypointProgress left = progressAt(firstA, 1, WaypointProgressStatus.COMPLETED);
        left.setArrivedAt(NOW.minusSeconds(1800));
        left.setDepartedAt(NOW.minusSeconds(600));
        left.setAccumulatedDwellSeconds(300);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(
                left,
                progressAt(pointB, 2, WaypointProgressStatus.NAVIGATING),
                progressAt(secondA, 3, WaypointProgressStatus.UPCOMING)
        ));
        TripWaypoint zoneA = zoneWaypoint(firstA, 1, List.of(memberA1, memberA2));
        zoneA.setVisitKind(com.aifishing.planning.spatial.TargetKind.ZONE);
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(
                zoneA,
                pointWaypoint(pointB, 2),
                zoneWaypoint(secondA, 3, List.of(memberA3, memberA4))
        ));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps()));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity moveA = deliveredMove(firstA);
        com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity moveB = deliveredMove(pointB);
        com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity stay = deliveredStay();
        when(deliveredDecisionRepository.findByFishingSessionIdOrderByCreatedAtAsc(SESSION_ID))
                .thenReturn(List.of(moveA, stay, moveB));

        FishingSessionState state = builder.build(SESSION_ID, new EnvironmentSnapshot(NOW, null));

        assertThat(state.opportunityRevisit().packageCooldowns()).hasSize(1);
        assertThat(state.opportunityRevisit().packageCooldowns().getFirst().packageMemberIds())
                .containsExactly(memberA1, memberA2);
        assertThat(state.opportunityRevisit().packageCooldowns().getFirst().packageMemberIds())
                .doesNotContain(memberA3, memberA4);
        assertThat(state.opportunityRevisit().packageCooldowns().getFirst().physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.opportunityRevisit().packageCooldowns().getFirst().cooldownUntil())
                .isEqualTo(NOW.minusSeconds(600).plusSeconds(60 * 60));
        assertThat(state.opportunityRevisit().lastMoveTargetTripWaypointIds())
                .containsExactly(firstA, pointB);
    }

    @Test
    void fishingSliceCopiesOpenAdHocStopWithoutReplacingOriginalPlan() {
        UUID stopId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
        UUID targetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02");
        UUID featureId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03");
        FishingSession session = session();
        session.setActivityState(com.aifishing.guidance.contracts.FishingActivityState.FISHING);
        session.setActivityStateSource(com.aifishing.guidance.contracts.ActivityStateSource.USER_AD_HOC);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake()));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(progress()));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(tripWaypoint()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps()));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        com.aifishing.fishingsession.domain.SessionAdHocFishingStop stop =
                new com.aifishing.fishingsession.domain.SessionAdHocFishingStop();
        stop.setId(stopId);
        stop.setStartedAt(NOW.minusSeconds(600));
        stop.setFishingTargetId(targetId);
        stop.setZoneId(ZONE_ID);
        stop.setLakeFeatureId(featureId);
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(stop));

        FishingSessionState state = builder.build(SESSION_ID, new EnvironmentSnapshot(NOW, null));

        assertThat(state.fishing().activityStateSource())
                .isEqualTo(com.aifishing.guidance.contracts.ActivityStateSource.USER_AD_HOC);
        assertThat(state.fishing().adHocFishingStopId()).isEqualTo(stopId);
        assertThat(state.fishing().adHocStartedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(state.fishing().fishingTargetId()).isEqualTo(targetId);
        assertThat(state.fishing().physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.fishing().lakeFeatureId()).isEqualTo(featureId);
        assertThat(state.fishing().currentTripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(state.plan().originalPlanSteps()).hasSize(1);
        assertThat(state.plan().originalPlanSteps().getFirst().tripWaypointId()).isEqualTo(TRIP_WP);
    }

    @Test
    void fishingSliceKeepsJustClosedAdHocStopWhenNoOpenStop() {
        UUID stopId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
        UUID targetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02");
        UUID featureId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03");
        FishingSession session = session();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake()));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(progress()));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(tripWaypoint()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(List.of(gps()));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION_ID)).thenReturn(List.of());
        when(empiricalPerformanceService.get(SESSION_ID, null)).thenReturn(emptyPerformance());
        when(planVersionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.empty());
        com.aifishing.fishingsession.domain.SessionAdHocFishingStop closed =
                new com.aifishing.fishingsession.domain.SessionAdHocFishingStop();
        closed.setId(stopId);
        closed.setStartedAt(NOW.minusSeconds(600));
        closed.setEndedAt(NOW.minusSeconds(5));
        closed.setFishingTargetId(targetId);
        closed.setZoneId(ZONE_ID);
        closed.setLakeFeatureId(featureId);
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNotNullOrderByEndedAtDesc(SESSION_ID))
                .thenReturn(Optional.of(closed));

        FishingSessionState state = builder.build(SESSION_ID, new EnvironmentSnapshot(NOW, null));

        assertThat(state.fishing().adHocFishingStopId()).isEqualTo(stopId);
        assertThat(state.fishing().adHocStartedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(state.fishing().fishingTargetId()).isEqualTo(targetId);
        assertThat(state.fishing().physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(state.fishing().lakeFeatureId()).isEqualTo(featureId);
    }

    private static TripWaypoint tripWaypoint() {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(TRIP_WP);
        waypoint.setTripPlanId(PLAN_ID);
        waypoint.setSequence(1);
        waypoint.setZoneId(ZONE_ID);
        waypoint.setMetadata(Map.of(
                TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                List.of(MEMBER_A.toString(), MEMBER_B.toString())
        ));
        return waypoint;
    }

    private static TripWaypoint zoneWaypoint(UUID id, int sequence, List<UUID> members) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setTripPlanId(PLAN_ID);
        waypoint.setSequence(sequence);
        waypoint.setZoneId(ZONE_ID);
        waypoint.setMetadata(Map.of(
                TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                members.stream().map(UUID::toString).toList()
        ));
        return waypoint;
    }

    private static TripWaypoint pointWaypoint(UUID id, int sequence) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setTripPlanId(PLAN_ID);
        waypoint.setSequence(sequence);
        return waypoint;
    }

    private static SessionWaypointProgress progressAt(UUID tripWaypointId, int sequence, WaypointProgressStatus status) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setId(UUID.randomUUID());
        row.setFishingSessionId(SESSION_ID);
        row.setTripWaypointId(tripWaypointId);
        row.setSequence(sequence);
        row.setStatus(status);
        return row;
    }

    private static FishingSession session() {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTripId(TRIP_ID);
        session.setTripPlanId(PLAN_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setStartedAt(NOW.minusSeconds(3600));
        return session;
    }

    private static Trip trip() {
        Trip trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setLakeId(LAKE_ID);
        trip.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        trip.setFishingEndTime(LocalTime.of(18, 0));
        return trip;
    }

    private static Lake lake() {
        Lake lake = new Lake();
        lake.setId(LAKE_ID);
        lake.setTimeZoneId("UTC");
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        lake.setCentroid(factory.createPoint(new Coordinate(-78.92, 44.75)));
        return lake;
    }

    private static SessionWaypointProgress progress() {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setId(PROGRESS_ID);
        row.setFishingSessionId(SESSION_ID);
        row.setTripWaypointId(TRIP_WP);
        row.setSequence(1);
        row.setStatus(WaypointProgressStatus.NAVIGATING);
        return row;
    }

    private static SessionLocationPoint gps() {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        SessionLocationPoint point = new SessionLocationPoint();
        point.setFishingSessionId(SESSION_ID);
        point.setLocation(factory.createPoint(new Coordinate(-78.92, 44.75)));
        point.setQuality(LocationQuality.ACCEPTED);
        point.setRecordedAt(NOW.minusSeconds(5));
        return point;
    }

    private static SessionPerformanceResponse emptyPerformance() {
        return new SessionPerformanceResponse(SESSION_ID, 0, 0, 0, 0, 0, 0, 0, 0.0, null, null, List.of());
    }

    private static com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity deliveredMove(UUID target) {
        com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity entity =
                new com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity();
        entity.setDecision(GuidanceContracts.mapper().convertValue(
                new com.aifishing.guidance.contracts.DeliveredDecision(
                        GuidanceSchemaVersion.VALUE,
                        SESSION_ID,
                        GuidanceAction.MOVE,
                        null,
                        target,
                        null,
                        null,
                        null,
                        null,
                        null,
                        15,
                        List.of("TEST"),
                        "Move",
                        List.of(),
                        false,
                        null,
                        null
                ),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                }
        ));
        return entity;
    }

    private static com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity deliveredStay() {
        com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity entity =
                new com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity();
        entity.setDecision(GuidanceContracts.mapper().convertValue(
                new com.aifishing.guidance.contracts.DeliveredDecision(
                        GuidanceSchemaVersion.VALUE,
                        SESSION_ID,
                        GuidanceAction.STAY,
                        null,
                        TRIP_WP,
                        null,
                        null,
                        null,
                        null,
                        null,
                        15,
                        List.of("TEST"),
                        "Stay",
                        List.of(),
                        false,
                        null,
                        null
                ),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                }
        ));
        return entity;
    }
}
