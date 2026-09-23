package com.aifishing.guidance.validate;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aifishing.guidance.GuidancePhase2Fixtures.AT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.OTHER_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_1;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.TRIP_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.candidate;
import static com.aifishing.guidance.GuidancePhase2Fixtures.retrieveCandidate;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safetyOk;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultDecisionValidatorOpportunityRevisitTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID MEMBER_A1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID MEMBER_A2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID MEMBER_A3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID MEMBER_A4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
    private static final UUID ZONE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

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
    void stayIsNotACooldownOrOscillationRevisit() {
        FishingSessionState state = withRevisit(
                TRIP_WAYPOINT,
                List.of(original(1, TRIP_WAYPOINT, List.of(MEMBER_A1, MEMBER_A2), "FISHING")),
                List.of(new FishingSessionState.PackageCooldown(
                        List.of(MEMBER_A1, MEMBER_A2), NOW.plusSeconds(1800), ZONE_ID)),
                List.of(TRIP_WAYPOINT, OTHER_WAYPOINT)
        );
        CandidateDecision stay = new CandidateDecision(
                GuidancePhase2Fixtures.safeState().schemaVersion(),
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.STAY,
                GuidanceAction.CHANGE_RETRIEVE,
                TRIP_WAYPOINT,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                15,
                List.of("BITE"),
                "Stay on the bite.",
                List.of(new HorizonStep(1, GuidanceAction.STAY, TRIP_WAYPOINT, 15, true)),
                0.76
        );

        DecisionValidationResult result = validator.validate(stay, state, safetyOk());

        assertThat(issuedChecks(result)).doesNotContain(
                DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN,
                DecisionValidationCheck.MOVE_OSCILLATION
        );
    }

    @Test
    void moveToCurrentLocationIsNotACooldownRevisit() {
        FishingSessionState state = withRevisit(
                TRIP_WAYPOINT,
                List.of(original(1, TRIP_WAYPOINT, List.of(MEMBER_A1), "FISHING")),
                List.of(new FishingSessionState.PackageCooldown(List.of(MEMBER_A1), NOW.plusSeconds(1800), null)),
                List.of()
        );

        DecisionValidationResult result = validator.validate(
                candidate(
                        GuidanceAction.MOVE,
                        GuidanceAction.CHANGE_LURE,
                        TRIP_WAYPOINT,
                        List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true)),
                        20
                ),
                state,
                safetyOk()
        );

        assertThat(issuedChecks(result)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
    }

    @Test
    void moveSamePhysicalIdentityWhileStillThereIsInvalid() {
        FishingSessionState state = withRevisit(
                TRIP_WAYPOINT,
                List.of(
                        original(1, TRIP_WAYPOINT, List.of(MEMBER_A1, MEMBER_A2), "FISHING"),
                        original(2, OTHER_WAYPOINT, List.of(MEMBER_A1, MEMBER_A2), "UPCOMING")
                ),
                List.of(),
                List.of()
        );

        DecisionValidationResult result = validator.validate(moveTo(OTHER_WAYPOINT), state, safetyOk());

        assertThat(issuedChecks(result)).contains(DecisionValidationCheck.WAYPOINT_ID_INCONSISTENT);
        assertThat(issuedChecks(result)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
    }

    @Test
    void cooledVisitPackageBlocksMoveAndLeavesDisjointSameZonePackageEligible() {
        FishingSessionState cooled = withRevisit(
                TRIP_WAYPOINT,
                List.of(
                        original(1, TRIP_WAYPOINT, List.of(), "FISHING"),
                        original(2, SPOT_1, List.of(MEMBER_A1, MEMBER_A2), "UPCOMING"),
                        original(3, SPOT_2, List.of(MEMBER_A3, MEMBER_A4), "UPCOMING")
                ),
                List.of(new FishingSessionState.PackageCooldown(
                        List.of(MEMBER_A1, MEMBER_A2), NOW.plusSeconds(1800), ZONE_ID)),
                List.of()
        );

        DecisionValidationResult blocked = validator.validate(moveTo(SPOT_1), cooled, safetyOk());
        DecisionValidationResult eligible = validator.validate(moveTo(SPOT_2), cooled, safetyOk());

        assertThat(issuedChecks(blocked)).contains(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
        assertThat(issuedChecks(eligible)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
        assertThat(eligible.valid()).isTrue();
    }

    @Test
    void retrieveDoesNotApplyCooldown() {
        FishingSessionState state = withRevisit(
                TRIP_WAYPOINT,
                List.of(original(1, SPOT_1, List.of(MEMBER_A1), "UPCOMING")),
                List.of(new FishingSessionState.PackageCooldown(List.of(MEMBER_A1), NOW.plusSeconds(1800), null)),
                List.of()
        );

        DecisionValidationResult result = validator.validate(retrieveCandidate(), state, safetyOk());

        assertThat(issuedChecks(result)).doesNotContain(
                DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN,
                DecisionValidationCheck.MOVE_OSCILLATION
        );
    }

    @Test
    void rapidDeliveredMoveAbaIsOscillation() {
        FishingSessionState state = withRevisit(
                SPOT_2,
                List.of(
                        original(1, SPOT_1, List.of(), "UPCOMING"),
                        original(2, SPOT_2, List.of(), "FISHING")
                ),
                List.of(),
                List.of(SPOT_1, SPOT_2)
        );

        DecisionValidationResult result = validator.validate(moveTo(SPOT_1), state, safetyOk());

        assertThat(issuedChecks(result)).contains(DecisionValidationCheck.MOVE_OSCILLATION);
    }

    @Test
    void oscillationAllowsReturnAfterCooldownExpiry() {
        FishingSessionState state = withRevisit(
                SPOT_2,
                List.of(
                        original(1, SPOT_1, List.of(MEMBER_A1), "UPCOMING"),
                        original(2, SPOT_2, List.of(), "FISHING")
                ),
                List.of(new FishingSessionState.PackageCooldown(List.of(MEMBER_A1), NOW.minusSeconds(1), null)),
                List.of(SPOT_1, SPOT_2)
        );

        DecisionValidationResult result = validator.validate(moveTo(SPOT_1), state, safetyOk());

        assertThat(issuedChecks(result)).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(result)).doesNotContain(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN);
    }

    @Test
    void biteOrFishOnAtReturnTargetDoesNotReopenOscillation() {
        FishingSessionState base = withRevisit(
                SPOT_2,
                List.of(
                        original(1, SPOT_1, List.of(), "UPCOMING"),
                        original(2, SPOT_2, List.of(), "FISHING")
                ),
                List.of(),
                List.of(SPOT_1, SPOT_2)
        );
        FishingSessionState state = withSummaries(base, List.of("Bite recorded", "Fish on"));
        CandidateDecision move = new CandidateDecision(
                moveTo(SPOT_1).schemaVersion(),
                moveTo(SPOT_1).decisionId(),
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                SPOT_1,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("BITE", "FISH_ON"),
                "Return to the bite.",
                List.of(new HorizonStep(1, GuidanceAction.MOVE, SPOT_1, null, true)),
                0.76
        );

        DecisionValidationResult result = validator.validate(move, state, safetyOk());

        assertThat(issuedChecks(result)).contains(DecisionValidationCheck.MOVE_OSCILLATION);
    }

    @Test
    void weatherPressureAndToolEvidenceReopenOscillation() {
        FishingSessionState oscillating = withRevisit(
                SPOT_2,
                List.of(
                        original(1, SPOT_1, List.of(), "UPCOMING"),
                        original(2, SPOT_2, List.of(), "FISHING")
                ),
                List.of(),
                List.of(SPOT_1, SPOT_2)
        );

        assertThat(issuedChecks(validator.validate(
                moveToWithReasons(SPOT_1, List.of("SIGNIFICANT_WEATHER")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(validator.validate(
                moveToWithReasons(SPOT_1, List.of("LIVE_PRESSURE")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(validator.validate(
                moveToWithReasons(SPOT_1, List.of("GET_NEARBY")), oscillating, safetyOk()
        ))).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
    }

    @Test
    void fishHereReopensOscillationAndBiteAtCurrentSupportsStayOnly() {
        FishingSessionState oscillating = withRevisit(
                SPOT_2,
                List.of(
                        original(1, SPOT_1, List.of(), "UPCOMING"),
                        original(2, SPOT_2, List.of(), "FISHING")
                ),
                List.of(),
                List.of(SPOT_1, SPOT_2)
        );
        FishingSessionState adHoc = withAdHoc(oscillating);

        DecisionValidationResult reopened = validator.validate(moveTo(SPOT_1), adHoc, safetyOk());
        DecisionValidationResult stay = validator.validate(
                new CandidateDecision(
                        oscillating.schemaVersion(),
                        GuidancePhase2Fixtures.DECISION_ID,
                        GuidanceAction.STAY,
                        null,
                        SPOT_2,
                        LureFamily.TUBE,
                        PresentationTechnique.STEADY_RETRIEVE,
                        3.0,
                        4.5,
                        RetrieveStyle.SLOW,
                        15,
                        List.of("FISH_ON"),
                        "Stay on the fish-on.",
                        List.of(new HorizonStep(1, GuidanceAction.STAY, SPOT_2, 15, true)),
                        0.8
                ),
                withSummaries(oscillating, List.of("Fish on")),
                safetyOk()
        );

        assertThat(issuedChecks(reopened)).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(issuedChecks(stay)).doesNotContain(DecisionValidationCheck.MOVE_OSCILLATION);
        assertThat(stay.valid()).isTrue();
    }

    private static CandidateDecision moveTo(UUID target) {
        return moveToWithReasons(target, List.of("CURRENT_SPOT_UNPRODUCTIVE"));
    }

    private static CandidateDecision moveToWithReasons(UUID target, List<String> reasons) {
        return new CandidateDecision(
                GuidancePhase2Fixtures.safeState().schemaVersion(),
                GuidancePhase2Fixtures.DECISION_ID,
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

    private static OriginalPlanStep original(int sequence, UUID tripWaypointId, List<UUID> members, String status) {
        return new OriginalPlanStep(sequence, tripWaypointId, ZONE_ID, null, null, members, status);
    }

    private static FishingSessionState withRevisit(
            UUID current,
            List<OriginalPlanStep> original,
            List<FishingSessionState.PackageCooldown> cooled,
            List<UUID> lastMoves
    ) {
        FishingSessionState base = GuidancePhase2Fixtures.state(
                com.aifishing.guidance.contracts.WeatherCondition.CLOUDY,
                16.0,
                4.5,
                12_000.0,
                1_500.0,
                90,
                current,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, current, null, true)),
                original
        );
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                fishingAt(base.fishing(), current),
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

    private static FishingSessionState.Fishing fishingAt(FishingSessionState.Fishing fishing, UUID current) {
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
                fishing.activityState(),
                fishing.activityStateSince(),
                fishing.activityStateSource(),
                fishing.activeFishingEffortMinutes(),
                fishing.noBiteMinutes(),
                fishing.adHocFishingStopId(),
                fishing.adHocStartedAt(),
                fishing.fishingTargetId(),
                fishing.physicalZoneId(),
                fishing.lakeFeatureId()
        );
    }

    private static FishingSessionState withSummaries(FishingSessionState state, List<String> summaries) {
        return new FishingSessionState(
                state.schemaVersion(),
                state.session(),
                state.position(),
                state.boat(),
                state.fishing(),
                state.environment(),
                new FishingSessionState.Recent(
                        state.recent().moveIds(),
                        state.recent().lureChangeIds(),
                        state.recent().catchEventIds(),
                        state.recent().adviceIds(),
                        state.recent().rejectedAdviceIds(),
                        summaries
                ),
                state.performance(),
                state.plan(),
                state.opportunityRevisit()
        );
    }

    private static FishingSessionState withAdHoc(FishingSessionState state) {
        FishingSessionState.Fishing fishing = state.fishing();
        return new FishingSessionState(
                state.schemaVersion(),
                state.session(),
                state.position(),
                state.boat(),
                new FishingSessionState.Fishing(
                        fishing.currentTripWaypointId(),
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
                        ActivityStateSource.USER_AD_HOC,
                        fishing.activeFishingEffortMinutes(),
                        fishing.noBiteMinutes(),
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01"),
                        AT,
                        fishing.fishingTargetId(),
                        fishing.physicalZoneId(),
                        fishing.lakeFeatureId()
                ),
                state.environment(),
                state.recent(),
                state.performance(),
                state.plan(),
                state.opportunityRevisit()
        );
    }

    private static Set<DecisionValidationCheck> issuedChecks(DecisionValidationResult result) {
        return result.issues().stream().map(ValidationIssue::check).collect(Collectors.toSet());
    }
}
