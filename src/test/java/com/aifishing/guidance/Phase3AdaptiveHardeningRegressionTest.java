package com.aifishing.guidance;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.feedback.catchlog.service.CatchAssociationService;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.feedback.effort.repo.SessionPauseIntervalRepository;
import com.aifishing.feedback.effort.service.FishingEffortService;
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
import com.aifishing.fishingsession.service.WaypointProgressMachine;
import com.aifishing.guidance.attribution.ComputedAttribution;
import com.aifishing.guidance.attribution.DeliveredSnapshot;
import com.aifishing.guidance.attribution.ObservedUserAction;
import com.aifishing.guidance.attribution.OutcomeAttributionCalculator;
import com.aifishing.guidance.attribution.OutcomeSignal;
import com.aifishing.guidance.attribution.UserActionInference;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.events.ActivityStateUpdater;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.guidance.metrics.GuidanceSuccessClassifier;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.validate.DefaultDecisionValidator;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aifishing.guidance.GuidancePhase2Fixtures.AT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.DECISION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_1;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_3;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safetyOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase3AdaptiveHardeningRegressionTest {

    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");
    private static final UUID PLAN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MEMBER_A1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID MEMBER_A2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID MEMBER_A3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID MEMBER_A4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
    private static final UUID ZONE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID AD_HOC_STOP = UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private static final double SPOT3_LAT = 44.75;
    private static final double SPOT3_LNG = -79.35;

    @Mock
    private SessionWaypointProgressRepository progressRepository;
    @Mock
    private SessionAdHocFishingStopRepository adHocStopRepository;
    @Mock
    private LiveWaypointActivityStore livePositionStore;
    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;
    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private SessionPauseIntervalRepository pauseIntervalRepository;
    @Mock
    private FishingEffortSegmentRepository segmentRepository;

    private DefaultDecisionValidator validator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        validator = new DefaultDecisionValidator(
                new SessionProperties(),
                new OpportunityRevisitSupport(new GuidanceProperties(), new SessionProperties(), clock)
        );
    }

    @Test
    void scenarioA_fishHereThenCommittedMoveSpot3PreservesOriginalAndExecutesAtSpot3() {
        List<OriginalPlanStep> original = original123();
        List<HorizonStep> committedMoveSpot3 = List.of(
                new HorizonStep(1, GuidanceAction.MOVE, SPOT_3, null, true)
        );
        FishingSessionState state = withPlan(
                originalState(SPOT_2, original, committedMoveSpot3),
                original,
                SPOT_3
        );

        assertThat(state.plan().originalPlanSteps()).extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);
        assertThat(state.plan().originalPlanSteps()).extracting(OriginalPlanStep::progressStatus)
                .containsExactly("COMPLETED", "NAVIGATING", "UPCOMING");
        assertThat(state.plan().activeGuidanceTargetTripWaypointId()).isEqualTo(SPOT_3);
        assertThat(ActiveGuidanceTarget.fromHorizon(committedMoveSpot3)).isEqualTo(SPOT_3);

        WaypointProgressMachine machine = new WaypointProgressMachine(new SessionProperties(), null);
        SessionWaypointProgress row2 = navigating(SPOT_2, 2);
        SessionWaypointProgress row3 = upcoming(SPOT_3, 3);
        Instant t0 = Instant.parse("2026-09-18T04:10:00Z");
        machine.applyAcceptedHistory(
                List.of(row2, row3),
                Map.of(SPOT_2, planned(SPOT_2, 44.80, -79.40), SPOT_3, planned(SPOT_3, SPOT3_LAT, SPOT3_LNG)),
                samples(t0, SPOT3_LAT, SPOT3_LNG),
                SPOT_3
        );
        assertThat(row3.getStatus()).isEqualTo(WaypointProgressStatus.FISHING);
        assertThat(row2.getStatus()).isEqualTo(WaypointProgressStatus.NAVIGATING);
        assertThat(row2.getSkippedAt()).isNull();
        assertThat(row2.getCompletedAt()).isNull();

        ActiveGuidanceTarget target = org.mockito.Mockito.mock(ActiveGuidanceTarget.class);
        when(target.resolve(org.mockito.ArgumentMatchers.any(FishingSession.class))).thenReturn(SPOT_3);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(
                progress(SPOT_2, 2, WaypointProgressStatus.NAVIGATING),
                progress(SPOT_3, 3, WaypointProgressStatus.UPCOMING)
        ));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(
                waypoint(SPOT_2, 2, 44.80, -79.40),
                waypoint(SPOT_3, 3, SPOT3_LAT, SPOT3_LNG)
        ));
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID)).thenReturn(List.of());
        CatchAssociationService.Association catchAtSpot3 = new CatchAssociationService(
                progressRepository, tripWaypointRepository, adHocStopRepository, new FeedbackProperties(), target
        ).associate(session(), null, t0.plusSeconds(60), point(SPOT3_LAT, SPOT3_LNG));
        assertThat(catchAtSpot3.tripWaypointId()).isEqualTo(SPOT_3);
        assertThat(catchAtSpot3.method()).isEqualTo(CatchAssociationMethod.NEAREST_WAYPOINT);

        ActivityStateUpdater updater = new ActivityStateUpdater(
                progressRepository, adHocStopRepository, livePositionStore, target);
        FishingSession activitySession = session();
        updater.refresh(activitySession, List.of(row2, row3), t0);
        assertThat(activitySession.getActivityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(activitySession.getActivityStateSource()).isEqualTo(ActivityStateSource.PROGRESS);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED
        )).thenReturn(List.of(gps("a", t0), gps("b", t0.plusSeconds(60))));
        when(pauseIntervalRepository.findByFishingSessionIdOrderByPausedAtAsc(SESSION_ID)).thenReturn(List.of());
        new FishingEffortService(
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
                target
        ).recompute(SESSION_ID);
        ArgumentCaptor<List<FishingEffortSegment>> saved = ArgumentCaptor.forClass(List.class);
        verify(segmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).anyMatch(segment ->
                segment.getSegmentType() == EffortSegmentType.FISHING
                        && SPOT_3.equals(segment.getTripWaypointId()));
        assertThat(saved.getValue()).noneMatch(segment -> SPOT_2.equals(segment.getTripWaypointId()));
    }

    @Test
    void scenarioB_horizonStayAtXThenMoveSpot2IsValid() {
        FishingSessionState adHoc = adHocState(original123(), List.of(
                new HorizonStep(1, GuidanceAction.STAY, null, 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, SPOT_2, null, false)
        ));
        CandidateDecision stayThenSpot2 = new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION_ID,
                GuidanceAction.STAY,
                null,
                null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("USER_STARTED_AD_HOC_FISHING"),
                "Stay at X then continue to Spot 2.",
                List.of(
                        new HorizonStep(1, GuidanceAction.STAY, null, 20, true),
                        new HorizonStep(2, GuidanceAction.MOVE, SPOT_2, null, false)
                ),
                0.8
        );

        DecisionValidationResult result = validator.validate(stayThenSpot2, adHoc, safetyOk());

        assertThat(result.valid()).isTrue();
        assertThat(issuedChecks(result)).doesNotContain(
                DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN,
                DecisionValidationCheck.MOVE_OSCILLATION,
                DecisionValidationCheck.WAYPOINT_NOT_FOUND
        );
        assertThat(stayThenSpot2.proposedHorizon()).extracting(HorizonStep::tripWaypointId)
                .containsExactly(null, SPOT_2)
                .doesNotContain(SPOT_3);
    }

    @Test
    void scenarioC_rapidOscillationBlockedUnlessMaterialChangeAndBiteIsNotAMoveBackTicket() {
        List<OriginalPlanStep> remaining = List.of(
                original(1, SPOT_1, List.of(), "UPCOMING"),
                original(2, SPOT_2, List.of(), "FISHING"),
                original(3, SPOT_3, List.of(), "UPCOMING")
        );
        FishingSessionState oscillating = withRevisit(
                SPOT_2,
                remaining,
                List.of(),
                List.of(SPOT_1, SPOT_2)
        );

        assertThat(issuedChecks(validator.validate(moveTo(SPOT_1), oscillating, safetyOk())))
                .contains(DecisionValidationCheck.MOVE_OSCILLATION);

        assertThat(issuedChecks(validator.validate(
                moveTo(SPOT_1, List.of("SIGNIFICANT_WEATHER")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(validator.validate(
                moveTo(SPOT_1, List.of("LIVE_PRESSURE")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(validator.validate(
                moveTo(SPOT_1, List.of("GET_NEARBY")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);

        FishingSessionState expired = withRevisit(
                SPOT_2,
                remaining,
                List.of(new FishingSessionState.PackageCooldown(List.of(SPOT_1), NOW.minusSeconds(1), null)),
                List.of(SPOT_1, SPOT_2)
        );
        assertThat(issuedChecks(validator.validate(moveTo(SPOT_1), expired, safetyOk())))
                .doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);

        assertThat(issuedChecks(validator.validate(moveTo(SPOT_1), adHoc(oscillating), safetyOk())))
                .doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);

        CandidateDecision biteMoveBack = moveTo(SPOT_1, List.of("BITE", "FISH_ON"));
        assertThat(issuedChecks(validator.validate(biteMoveBack, oscillating, safetyOk())))
                .contains(DecisionValidationCheck.MOVE_OSCILLATION);
        CandidateDecision stayOnBite = stay(SPOT_2, List.of("BITE", "FISH_ON"), "Stay on the bite.");
        DecisionValidationResult stayResult = validator.validate(stayOnBite, oscillating, safetyOk());
        assertThat(stayResult.valid()).isTrue();
        assertThat(issuedChecks(stayResult)).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
    }

    @Test
    void scenarioD_visitPackageCoolsWhileDisjointSameZonePackageStaysEligible() {
        FishingSessionState cooled = withRevisit(
                SPOT_1,
                List.of(
                        original(1, SPOT_1, List.of(), "FISHING"),
                        original(2, SPOT_2, List.of(MEMBER_A1, MEMBER_A2), "UPCOMING"),
                        original(3, SPOT_3, List.of(MEMBER_A3, MEMBER_A4), "UPCOMING")
                ),
                List.of(new FishingSessionState.PackageCooldown(
                        List.of(MEMBER_A1, MEMBER_A2), NOW.plusSeconds(1800), ZONE_ID)),
                List.of()
        );

        DecisionValidationResult blocked = validator.validate(moveTo(SPOT_2), cooled, safetyOk());
        DecisionValidationResult eligible = validator.validate(moveTo(SPOT_3), cooled, safetyOk());

        assertThat(issuedChecks(blocked)).contains(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
        assertThat(issuedChecks(eligible)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
        assertThat(eligible.valid()).isTrue();
        assertThat(cooled.opportunityRevisit().packageCooldowns().getFirst().packageMemberIds())
                .containsExactly(MEMBER_A1, MEMBER_A2)
                .doesNotContain(MEMBER_A3, MEMBER_A4);
        assertThat(cooled.opportunityRevisit().packageCooldowns())
                .allMatch(row -> row.packageMemberIds() != null);
    }

    @Test
    void scenarioE_repeatedBiteFishOnSupportsStayAndCooldownDoesNotForceMove() {
        FishingSessionState coolingHere = withRevisit(
                SPOT_2,
                original123(),
                List.of(new FishingSessionState.PackageCooldown(List.of(SPOT_2), NOW.plusSeconds(1800), null)),
                List.of(SPOT_1)
        );
        CandidateDecision stayOnFish = stay(SPOT_2, List.of("BITE", "FISH_ON", "BITE"), "Stay on repeated fish-on.");

        DecisionValidationResult stay = validator.validate(stayOnFish, coolingHere, safetyOk());
        DecisionValidationResult moveAway = validator.validate(moveTo(SPOT_3), coolingHere, safetyOk());

        assertThat(stay.valid()).isTrue();
        assertThat(issuedChecks(stay)).doesNotContain(
                DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN,
                DecisionValidationCheck.MOVE_OSCILLATION
        );
        assertThat(moveAway.valid()).isTrue();
        assertThat(issuedChecks(moveAway)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
    }

    @Test
    void scenarioF_agentTimeoutAfterFishHereKeepsActivityEffortCatchAndOriginalPlan() {
        FishingSessionState adHoc = adHocState(original123(), List.of(
                new HorizonStep(1, GuidanceAction.STAY, null, 15, true)
        ));
        DeliveredDecision fallback = GuidanceFallback.stay(
                DECISION_ID,
                adHoc,
                GuidanceFallback.RUN_TIMEOUT,
                "Timed out during Fish Here"
        );

        assertThat(fallback.primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(fallback.fallbackUsed()).isTrue();
        assertThat(fallback.fallbackReason()).isEqualTo(GuidanceFallback.RUN_TIMEOUT);
        assertThat(fallback.targetTripWaypointId()).isNull();
        assertThat(adHoc.plan().originalPlanSteps()).extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);
        assertThat(adHoc.plan().originalPlanSteps()).extracting(OriginalPlanStep::progressStatus)
                .containsExactly("COMPLETED", "NAVIGATING", "UPCOMING");
        assertThat(adHoc.plan().activeGuidanceTargetTripWaypointId()).isNull();
        assertThat(ActiveGuidanceTarget.fromHorizon(fallback.proposedHorizon())).isNull();
        assertThat(adHoc.fishing().activityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(adHoc.fishing().activityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        assertThat(new TriggerClippedFishingAgentContextBuilder()
                .build(adHoc, com.aifishing.guidance.contracts.GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                        com.aifishing.guidance.contracts.RetrievedMemory.empty())
                .originalPlanSteps()).extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);

        SessionAdHocFishingStop stop = openStop(AT);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID))
                .thenReturn(List.of(progress(SPOT_2, 2, WaypointProgressStatus.NAVIGATING)));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID))
                .thenReturn(List.of(waypoint(SPOT_2, 2, 44.80, -79.40)));
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID)).thenReturn(List.of(stop));
        CatchAssociationService.Association association = new CatchAssociationService(
                progressRepository, tripWaypointRepository, adHocStopRepository, new FeedbackProperties(), null
        ).associate(session(), null, AT.plusSeconds(120), point(SPOT3_LAT, SPOT3_LNG));
        assertThat(association.method()).isEqualTo(CatchAssociationMethod.AD_HOC_STOP);
        assertThat(association.adHocFishingStopId()).isEqualTo(AD_HOC_STOP);
        assertThat(association.tripWaypointId()).isNull();

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        Instant t0 = Instant.parse("2026-09-18T04:20:00Z");
        when(locationPointRepository.findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
                SESSION_ID, LocationQuality.ACCEPTED
        )).thenReturn(List.of(gps("a", t0), gps("b", t0.plusSeconds(60))));
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID))
                .thenReturn(List.of(progress(SPOT_2, 2, WaypointProgressStatus.NAVIGATING)));
        when(adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(SESSION_ID))
                .thenReturn(List.of(openStop(t0)));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID))
                .thenReturn(List.of(waypoint(SPOT_2, 2, 44.80, -79.40)));
        when(pauseIntervalRepository.findByFishingSessionIdOrderByPausedAtAsc(SESSION_ID)).thenReturn(List.of());
        new FishingEffortService(
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
                null
        ).recompute(SESSION_ID);
        ArgumentCaptor<List<FishingEffortSegment>> saved = ArgumentCaptor.forClass(List.class);
        verify(segmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty();
        assertThat(saved.getValue()).allMatch(segment ->
                segment.getSegmentType() == EffortSegmentType.FISHING
                        && segment.getTripWaypointId() == null);
    }

    @Test
    void scenarioG_gotItWithoutObservationIsNotFollowedAndIsNotAcceptOrDecline() {
        DeliveredSnapshot retrieve = new DeliveredSnapshot(DECISION_ID, DECISION_ID, NOW, retrieveDecision());
        List<ObservedUserAction> ack = UserActionInference.infer(
                retrieve,
                SessionEventType.ADVICE_ACKNOWLEDGED,
                NOW.plusSeconds(5),
                Map.of("sourceEventId", "got-it")
        );
        assertThat(ack).isEmpty();
        assertThat(UserActionInference.feedbackEventType(SessionEventType.ADVICE_ACKNOWLEDGED))
                .isEqualTo(FeedbackStatus.ACKNOWLEDGED)
                .isNotEqualTo(FeedbackStatus.ACCEPTED)
                .isNotEqualTo(FeedbackStatus.REJECTED)
                .isNotEqualTo(FeedbackStatus.PARTIALLY_FOLLOWED);

        List<ComputedAttribution> rows = OutcomeAttributionCalculator.compute(
                NOW.plusSeconds(6 * 60),
                new GuidanceProperties.Attribution(),
                List.of(retrieve),
                ack,
                List.of(new OutcomeSignal(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        NOW.plusSeconds(5 * 60),
                        OutcomeKind.FISH_ON,
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                ))
        );
        assertThat(rows).noneMatch(ComputedAttribution::followedRecommendation);
        assertThat(rows).noneMatch(row ->
                GuidanceSuccessClassifier.classify(
                        row.deliveredDecisionId(),
                        row.followedRecommendation(),
                        row.outcomeKind()
                ) == GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(rows).noneMatch(row ->
                GuidanceSuccessClassifier.classify(
                        row.deliveredDecisionId(),
                        row.followedRecommendation(),
                        row.outcomeKind()
                ) == GuidanceSuccessKind.FISH_ON_SUCCESS
                        || Boolean.TRUE.equals(row.followedRecommendation()));
    }

    private static CandidateDecision moveTo(UUID target) {
        return moveTo(target, List.of("CURRENT_SPOT_UNPRODUCTIVE"));
    }

    private static CandidateDecision moveTo(UUID target, List<String> reasons) {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION_ID,
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                target,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                reasons,
                "Move to the next eligible stop.",
                List.of(new HorizonStep(1, GuidanceAction.MOVE, target, null, true)),
                0.76
        );
    }

    private static CandidateDecision stay(UUID target, List<String> reasons, String explanation) {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION_ID,
                GuidanceAction.STAY,
                null,
                target,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                15,
                reasons,
                explanation,
                List.of(new HorizonStep(1, GuidanceAction.STAY, target, 15, true)),
                0.8
        );
    }

    private static List<OriginalPlanStep> original123() {
        return List.of(
                original(1, SPOT_1, List.of(), "COMPLETED"),
                original(2, SPOT_2, List.of(), "NAVIGATING"),
                original(3, SPOT_3, List.of(), "UPCOMING")
        );
    }

    private static OriginalPlanStep original(int sequence, UUID tripWaypointId, List<UUID> members, String status) {
        return new OriginalPlanStep(sequence, tripWaypointId, ZONE_ID, null, null, members, status);
    }

    private static FishingSessionState originalState(
            UUID current,
            List<OriginalPlanStep> original,
            List<HorizonStep> horizon
    ) {
        return GuidancePhase2Fixtures.state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90, current, horizon, original
        );
    }

    private static FishingSessionState withPlan(
            FishingSessionState base,
            List<OriginalPlanStep> original,
            UUID activeTarget
    ) {
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                base.fishing(),
                base.environment(),
                base.recent(),
                base.performance(),
                new FishingSessionState.Plan(
                        base.plan().originalTripPlanId(),
                        base.plan().currentGuidancePlanVersion(),
                        base.plan().currentStep(),
                        base.plan().shortHorizonSteps(),
                        original,
                        activeTarget
                ),
                base.opportunityRevisit()
        );
    }

    private static FishingSessionState withRevisit(
            UUID current,
            List<OriginalPlanStep> original,
            List<FishingSessionState.PackageCooldown> cooled,
            List<UUID> lastMoves
    ) {
        FishingSessionState base = originalState(
                current,
                original,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, current, null, true))
        );
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                fishingAt(base.fishing(), current, false),
                base.environment(),
                base.recent(),
                base.performance(),
                new FishingSessionState.Plan(
                        base.plan().originalTripPlanId(),
                        base.plan().currentGuidancePlanVersion(),
                        base.plan().currentStep(),
                        base.plan().shortHorizonSteps(),
                        original,
                        base.plan().activeGuidanceTargetTripWaypointId()
                ),
                new FishingSessionState.OpportunityRevisit(cooled, lastMoves)
        );
    }

    private static FishingSessionState adHocState(List<OriginalPlanStep> original, List<HorizonStep> horizon) {
        FishingSessionState base = originalState(SPOT_2, original, horizon);
        return adHoc(withPlan(base, original, null));
    }

    private static FishingSessionState adHoc(FishingSessionState state) {
        return new FishingSessionState(
                state.schemaVersion(),
                state.session(),
                state.position(),
                state.boat(),
                fishingAt(state.fishing(), state.fishing().currentTripWaypointId(), true),
                state.environment(),
                state.recent(),
                state.performance(),
                state.plan(),
                state.opportunityRevisit()
        );
    }

    private static FishingSessionState.Fishing fishingAt(
            FishingSessionState.Fishing fishing,
            UUID current,
            boolean adHoc
    ) {
        return new FishingSessionState.Fishing(
                current,
                fishing.currentSessionWaypointProgressId(),
                fishing.structureType(),
                fishing.depthMinM(),
                fishing.depthMaxM(),
                fishing.lureFamily(),
                fishing.presentation(),
                fishing.retrieveStyle(),
                fishing.timeAtWaypointMinutes(),
                FishingActivityState.FISHING,
                fishing.activityStateSince(),
                adHoc ? ActivityStateSource.USER_AD_HOC : fishing.activityStateSource(),
                fishing.activeFishingEffortMinutes(),
                fishing.noBiteMinutes(),
                adHoc ? AD_HOC_STOP : fishing.adHocFishingStopId(),
                adHoc ? AT : fishing.adHocStartedAt(),
                fishing.fishingTargetId(),
                fishing.physicalZoneId(),
                fishing.lakeFeatureId()
        );
    }

    private static Set<DecisionValidationCheck> issuedChecks(DecisionValidationResult result) {
        return result.issues().stream().map(ValidationIssue::check).collect(Collectors.toSet());
    }

    private static FishingSession session() {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setTripPlanId(PLAN_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setStartedAt(AT);
        session.setActivityState(FishingActivityState.TRANSIT);
        session.setActivityStateSource(ActivityStateSource.PROGRESS);
        return session;
    }

    private static TripWaypoint planned(UUID id, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setLocation(point(lat, lng));
        waypoint.setEntryPoint(point(lat, lng));
        return waypoint;
    }

    private static TripWaypoint waypoint(UUID id, int sequence, double lat, double lng) {
        TripWaypoint waypoint = planned(id, lat, lng);
        waypoint.setSequence(sequence);
        waypoint.setTripPlanId(PLAN_ID);
        return waypoint;
    }

    private static SessionWaypointProgress navigating(UUID waypointId, int sequence) {
        SessionWaypointProgress row = progress(waypointId, sequence, WaypointProgressStatus.NAVIGATING);
        return row;
    }

    private static SessionWaypointProgress upcoming(UUID waypointId, int sequence) {
        return progress(waypointId, sequence, WaypointProgressStatus.UPCOMING);
    }

    private static SessionWaypointProgress progress(UUID tripWaypointId, int sequence, WaypointProgressStatus status) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setTripWaypointId(tripWaypointId);
        row.setSequence(sequence);
        row.setStatus(status);
        return row;
    }

    private static List<SessionLocationPoint> samples(Instant t0, double lat, double lng) {
        return List.of(
                gps("a", t0, lat, lng),
                gps("b", t0.plusSeconds(10), lat, lng),
                gps("c", t0.plusSeconds(20), lat, lng),
                gps("d", t0.plusSeconds(30), lat, lng)
        );
    }

    private static SessionLocationPoint gps(String id, Instant at) {
        return gps(id, at, SPOT3_LAT, SPOT3_LNG);
    }

    private static SessionLocationPoint gps(String id, Instant at, double lat, double lng) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setId(UUID.randomUUID());
        point.setClientPointId(id);
        point.setRecordedAt(at);
        point.setQuality(LocationQuality.ACCEPTED);
        point.setLocation(point(lat, lng));
        return point;
    }

    private static SessionAdHocFishingStop openStop(Instant startedAt) {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setId(AD_HOC_STOP);
        stop.setStartedAt(startedAt);
        stop.setLocation(point(SPOT3_LAT, SPOT3_LNG));
        return stop;
    }

    private static Point point(double lat, double lng) {
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }

    private static DeliveredDecision retrieveDecision() {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION_ID,
                GuidanceAction.CHANGE_RETRIEVE,
                null,
                null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("TEST"),
                "Change retrieve",
                List.of(),
                false,
                null,
                0.8
        );
    }
}
