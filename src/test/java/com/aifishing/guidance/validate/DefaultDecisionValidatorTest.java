package com.aifishing.guidance.validate;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SafetyConstraintCode;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.contracts.WeatherCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.DECISION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.OTHER_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.PROGRESS_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.TRIP_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.candidate;
import static com.aifishing.guidance.GuidancePhase2Fixtures.moveCandidate;
import static com.aifishing.guidance.GuidancePhase2Fixtures.retrieveCandidate;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safeState;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safetyBlock;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safetyOk;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safetyWarning;
import static com.aifishing.guidance.GuidancePhase2Fixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultDecisionValidatorTest {

    private DefaultDecisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultDecisionValidator(new SessionProperties());
    }

    @Test
    void moveOnSafeStatePasses() {
        DecisionValidationResult result = validator.validate(moveCandidate(), safeState(), safetyOk());

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void changeRetrieveDoesNotFailForUnrelatedNavOrNoFishingGaps() {
        FishingSessionState incomplete = state(
                WeatherCondition.CLOUDY, 16.0, null, null, null, null, null, List.of());
        SafetyVerdict zone = safetyWarning(
                SafetyConstraintCode.NO_FISHING_ZONE, List.of(GuidanceAction.values()));

        DecisionValidationResult result = validator.validate(retrieveCandidate(), incomplete, zone);

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(issuedChecks(result)).doesNotContain(
                DecisionValidationCheck.WAYPOINT_NOT_FOUND,
                DecisionValidationCheck.WAYPOINT_NOT_NAVIGABLE,
                DecisionValidationCheck.WAYPOINT_ID_INCONSISTENT,
                DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION,
                DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE,
                DecisionValidationCheck.GPS_ACCURACY_UNACCEPTABLE,
                DecisionValidationCheck.INSUFFICIENT_REMAINING_RANGE,
                DecisionValidationCheck.INSUFFICIENT_RETURN_RESERVE,
                DecisionValidationCheck.INSUFFICIENT_REMAINING_TIME,
                DecisionValidationCheck.NO_FISHING_ZONE,
                DecisionValidationCheck.REQUIRED_DATA_MISSING
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = DecisionValidationCheck.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OPPORTUNITY_IN_COOLDOWN", "MOVE_OSCILLATION"}
    )
    void eachCheckIsEmittedWhenApplicable(DecisionValidationCheck check) {
        Case scenario = scenarioFor(check);
        DecisionValidationResult result = validator.validate(scenario.candidate(), scenario.state(), scenario.verdict());

        assertThat(result.valid()).isFalse();
        assertThat(issuedChecks(result)).contains(check);
    }

    @Test
    void weatherCheckTrustsVerdictAndDoesNotRecomputeWind() {
        FishingSessionState gale = state(
                WeatherCondition.CLOUDY, 80.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))
        );

        DecisionValidationResult trustedOk = validator.validate(moveCandidate(), gale, safetyOk());
        assertThat(issuedChecks(trustedOk)).doesNotContain(DecisionValidationCheck.WEATHER_UNSAFE);

        DecisionValidationResult fromVerdict = validator.validate(
                moveCandidate(),
                gale,
                safetyWarning(
                        SafetyConstraintCode.WIND_UNSAFE,
                        List.of(GuidanceAction.STAY, GuidanceAction.CHANGE_RETRIEVE, GuidanceAction.RETURN)
                )
        );
        assertThat(issuedChecks(fromVerdict)).contains(DecisionValidationCheck.WEATHER_UNSAFE);
    }

    @Test
    void moveToEligibleOriginalPlanStepOutsideShortHorizonPasses() {
        List<UUID> ids = sixWaypointIds();
        UUID target = ids.get(5);
        FishingSessionState truncated = sixStepState(ids, "UPCOMING", 12_000.0, 1_500.0, 90);
        CandidateDecision move = candidate(
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                target,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, target, null, true)),
                20
        );

        DecisionValidationResult result = validator.validate(move, truncated, safetyOk());

        assertThat(result.valid()).isTrue();
        assertThat(issuedChecks(result)).doesNotContain(
                DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION,
                DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE
        );
    }

    @Test
    void moveToUnknownOrCompletedOriginalStepStillFails() {
        List<UUID> ids = sixWaypointIds();
        FishingSessionState truncated = sixStepState(ids, "COMPLETED", 12_000.0, 1_500.0, 90);
        UUID target = ids.get(5);

        DecisionValidationResult completed = validator.validate(
                candidate(
                        GuidanceAction.MOVE,
                        GuidanceAction.CHANGE_LURE,
                        target,
                        List.of(new HorizonStep(1, GuidanceAction.MOVE, target, null, true)),
                        20
                ),
                truncated,
                safetyOk()
        );
        assertThat(issuedChecks(completed)).contains(DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION);

        DecisionValidationResult unknown = validator.validate(
                candidate(
                        GuidanceAction.MOVE,
                        GuidanceAction.CHANGE_LURE,
                        OTHER_WAYPOINT,
                        List.of(new HorizonStep(1, GuidanceAction.MOVE, OTHER_WAYPOINT, null, true)),
                        20
                ),
                truncated,
                safetyOk()
        );
        assertThat(issuedChecks(unknown)).contains(DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION);
    }

    @Test
    void moveOutsideShortHorizonStillEnforcesRangeAndReturn() {
        List<UUID> ids = sixWaypointIds();
        UUID target = ids.get(5);
        CandidateDecision move = candidate(
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                target,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, target, null, true)),
                20
        );
        FishingSessionState shortRange = sixStepState(ids, "UPCOMING", 400.0, 1_500.0, 90);

        DecisionValidationResult result = validator.validate(move, shortRange, safetyOk());

        assertThat(result.valid()).isFalse();
        assertThat(issuedChecks(result)).contains(
                DecisionValidationCheck.INSUFFICIENT_REMAINING_RANGE,
                DecisionValidationCheck.INSUFFICIENT_RETURN_RESERVE
        );
        assertThat(issuedChecks(result)).doesNotContain(DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION);
    }

    private static List<UUID> sixWaypointIds() {
        return List.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000004"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000005"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000006")
        );
    }

    private static FishingSessionState sixStepState(
            List<UUID> ids,
            String lastStatus,
            Double remainingRangeMeters,
            Double returnReserveMeters,
            Integer remainingTimeMinutes
    ) {
        List<HorizonStep> horizon = List.of(
                new HorizonStep(1, GuidanceAction.STAY, ids.get(0), 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, ids.get(1), null, false),
                new HorizonStep(3, GuidanceAction.MOVE, ids.get(2), null, false),
                new HorizonStep(4, GuidanceAction.MOVE, ids.get(3), null, false)
        );
        List<OriginalPlanStep> original = List.of(
                original(1, ids.get(0), "FISHING"),
                original(2, ids.get(1), "UPCOMING"),
                original(3, ids.get(2), "UPCOMING"),
                original(4, ids.get(3), "UPCOMING"),
                original(5, ids.get(4), "UPCOMING"),
                original(6, ids.get(5), lastStatus)
        );
        return state(
                WeatherCondition.CLOUDY, 16.0, 4.5, remainingRangeMeters, returnReserveMeters, remainingTimeMinutes,
                ids.get(0), horizon, original
        );
    }

    private static OriginalPlanStep original(int sequence, UUID tripWaypointId, String status) {
        return new OriginalPlanStep(sequence, tripWaypointId, null, null, null, List.of(), status);
    }

    @Test
    void blockVerdictWithPrescribedReturnRejectsMoveViaAllowedActions() {
        SafetyVerdict block = safetyBlock(SafetyConstraintCode.THUNDERSTORM, GuidanceAction.RETURN);

        DecisionValidationResult result = validator.validate(moveCandidate(), safeState(), block);

        assertThat(result.valid()).isFalse();
        assertThat(issuedChecks(result)).contains(DecisionValidationCheck.WEATHER_UNSAFE);
        assertThat(block.prescribedAction()).isEqualTo(GuidanceAction.RETURN);
    }

    private static Case scenarioFor(DecisionValidationCheck check) {
        return switch (check) {
            case ACTION_INCOMPATIBLE -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            GuidanceAction.RETURN,
                            TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case HORIZON_COMMIT_INVALID -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            GuidanceAction.CHANGE_LURE,
                            TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, false)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case WAYPOINT_NOT_FOUND -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            null,
                            null,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, null, null, true)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case WAYPOINT_NOT_NAVIGABLE -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, 4.5, 0.0, 1_500.0, 90, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case WAYPOINT_ID_INCONSISTENT -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            GuidanceAction.CHANGE_LURE,
                            PROGRESS_ID,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, PROGRESS_ID, null, true)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case WAYPOINT_NOT_IN_SESSION -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            GuidanceAction.CHANGE_LURE,
                            OTHER_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, OTHER_WAYPOINT, null, true)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case WAYPOINT_NOT_ON_LAKE -> new Case(
                    candidate(
                            GuidanceAction.MOVE,
                            GuidanceAction.CHANGE_LURE,
                            OTHER_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, OTHER_WAYPOINT, null, true)),
                            20
                    ),
                    safeState(),
                    safetyOk()
            );
            case GPS_ACCURACY_UNACCEPTABLE -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, 80.0, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case INSUFFICIENT_REMAINING_RANGE -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, 4.5, 400.0, 1_500.0, 90, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case INSUFFICIENT_RETURN_RESERVE -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, 4.5, 400.0, 1_500.0, 90, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case INSUFFICIENT_REMAINING_TIME -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 0, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case WEATHER_UNSAFE -> new Case(
                    moveCandidate(),
                    safeState(),
                    safetyWarning(
                            SafetyConstraintCode.WIND_UNSAFE,
                            List.of(GuidanceAction.STAY, GuidanceAction.CHANGE_RETRIEVE, GuidanceAction.RETURN)
                    )
            );
            case NO_FISHING_ZONE -> new Case(
                    moveCandidate(),
                    safeState(),
                    safetyWarning(SafetyConstraintCode.NO_FISHING_ZONE, List.of(GuidanceAction.values()))
            );
            case SCHEMA_INVALID -> new Case(
                    new CandidateDecision(
                            "not-a-schema",
                            DECISION_ID,
                            GuidanceAction.MOVE,
                            GuidanceAction.CHANGE_LURE,
                            TRIP_WAYPOINT,
                            LureFamily.TUBE,
                            PresentationTechnique.STEADY_RETRIEVE,
                            3.0,
                            4.5,
                            RetrieveStyle.SLOW,
                            20,
                            List.of("CURRENT_SPOT_UNPRODUCTIVE"),
                            "Focused Phase 2 validator fixture.",
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true)),
                            0.76
                    ),
                    safeState(),
                    safetyOk()
            );
            case REQUIRED_DATA_MISSING -> new Case(
                    moveCandidate(),
                    state(WeatherCondition.CLOUDY, 16.0, null, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT,
                            List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true))),
                    safetyOk()
            );
            case OPPORTUNITY_IN_COOLDOWN, MOVE_OSCILLATION -> new Case(
                    moveCandidate(),
                    safeState(),
                    safetyOk()
            );
        };
    }

    private static Set<DecisionValidationCheck> issuedChecks(DecisionValidationResult result) {
        return result.issues().stream().map(ValidationIssue::check).collect(java.util.stream.Collectors.toSet());
    }

    private record Case(CandidateDecision candidate, FishingSessionState state, SafetyVerdict verdict) {
    }
}
