package com.aifishing.guidance.safety;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.SafetyConstraintCode;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.SafetyVerdictLevel;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.planning.PlanningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static com.aifishing.guidance.GuidancePhase2Fixtures.TRIP_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safeState;
import static com.aifishing.guidance.GuidancePhase2Fixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultSafetyRuleEngineTest {

    private static final Pattern ACTION_IN_REASON = Pattern.compile("\\b(STAY|RETURN)\\b");

    private DefaultSafetyRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties());
    }

    @Test
    void calmStateIsOkWithFullActionSetAndNullPrescribedAction() {
        SafetyVerdict verdict = engine.evaluate(safeState());

        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.OK);
        assertThat(verdict.prescribedAction()).isNull();
        assertThat(verdict.constraintCodes()).isEmpty();
        assertThat(verdict.allowedActions()).containsExactly(GuidanceAction.values());
    }

    @Test
    void windAtPenaltyThresholdIsWarningAndDoesNotPrescribeAction() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.CLOUDY, 25.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.WARNING);
        assertThat(verdict.prescribedAction()).isNull();
        assertThat(verdict.constraintCodes()).containsExactly(SafetyConstraintCode.WIND_UNSAFE);
        assertThat(verdict.allowedActions()).doesNotContain(GuidanceAction.MOVE);
        assertThat(verdict.allowedActions()).contains(GuidanceAction.STAY, GuidanceAction.CHANGE_RETRIEVE, GuidanceAction.RETURN);
        assertReasonsDoNotEncodeActions(verdict);
    }

    @Test
    void windAtHardRejectIsBlockWithRequiredPrescribedAction() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.CLOUDY, 40.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertBlockReturn(verdict, SafetyConstraintCode.WIND_UNSAFE);
    }

    @Test
    void thunderstormIsBlockWithRequiredPrescribedAction() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.THUNDERSTORM, 16.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.BLOCK);
        assertThat(verdict.prescribedAction()).isEqualTo(GuidanceAction.RETURN);
        assertThat(verdict.allowedActions()).containsExactly(GuidanceAction.RETURN);
        assertThat(verdict.constraintCodes()).contains(
                SafetyConstraintCode.THUNDERSTORM, SafetyConstraintCode.LIGHTNING);
        assertReasonsDoNotEncodeActions(verdict);
    }

    @Test
    void rangeAtOrBelowReserveIsBlockWithRequiredPrescribedAction() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 800.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertBlockReturn(verdict, SafetyConstraintCode.RANGE_INSUFFICIENT);
        assertThat(verdict.constraintCodes()).contains(SafetyConstraintCode.RETURN_RESERVE_INSUFFICIENT);
    }

    @Test
    void poorGpsIsWarningWithoutPrescribedAction() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.CLOUDY, 16.0, 80.0, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.WARNING);
        assertThat(verdict.prescribedAction()).isNull();
        assertThat(verdict.constraintCodes()).containsExactly(SafetyConstraintCode.GPS_ACCURACY_UNACCEPTABLE);
        assertThat(verdict.allowedActions()).doesNotContain(GuidanceAction.MOVE);
        assertReasonsDoNotEncodeActions(verdict);
    }

    @Test
    void missingWindIsNotTreatedAsUnsafe() {
        SafetyVerdict verdict = engine.evaluate(state(
                WeatherCondition.CLOUDY, null, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        ));

        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.OK);
        assertThat(verdict.constraintCodes()).isEmpty();
        assertThat(verdict.prescribedAction()).isNull();
    }

    private static void assertBlockReturn(SafetyVerdict verdict, SafetyConstraintCode expectedCode) {
        assertThat(verdict.level()).isEqualTo(SafetyVerdictLevel.BLOCK);
        assertThat(verdict.prescribedAction()).isNotNull().isEqualTo(GuidanceAction.RETURN);
        assertThat(verdict.allowedActions()).containsExactly(GuidanceAction.RETURN);
        assertThat(verdict.constraintCodes()).contains(expectedCode);
        assertReasonsDoNotEncodeActions(verdict);
    }

    private static void assertReasonsDoNotEncodeActions(SafetyVerdict verdict) {
        assertThat(verdict.reasons()).isNotEmpty();
        assertThat(verdict.reasons()).allMatch(reason -> !ACTION_IN_REASON.matcher(reason).find());
    }
}
