package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReplayMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCaseScorerTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-bbbb-4000-8000-000000000001");
    private static final UUID TARGET_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TARGET_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void uniqueExactPrimaryActionMustMatch() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.PLATFORM_REGRESSION,
                List.of(GuidanceAction.RETURN),
                List.of(GuidanceAction.MOVE),
                GuidanceAction.RETURN,
                GuidanceAction.RETURN,
                null,
                OutcomeKind.NONE,
                List.of()
        );
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.RETURN)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.STAY)).status())
                .isEqualTo(EvalCaseResultStatus.FAIL);
    }

    @Test
    void allowedForbiddenWithoutExactDoesNotRequirePrimaryEquality() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.PLATFORM_REGRESSION,
                List.of(GuidanceAction.STAY, GuidanceAction.CHANGE_LURE),
                List.of(GuidanceAction.RETURN),
                null,
                GuidanceAction.STAY,
                null,
                OutcomeKind.NONE,
                List.of()
        );
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.CHANGE_LURE)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.RETURN)).status())
                .isEqualTo(EvalCaseResultStatus.FAIL);
    }

    @Test
    void simulationAlwaysSkips() {
        EvalCaseFixture fixture = new EvalCaseFixture(
                "sim/deferred",
                EvalSuiteKind.SIMULATION,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                "scenario-sim",
                null,
                EvalCaseScorer.SIMULATION_DEFERRED
        );
        EvalCaseResult result = EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.STAY));
        assertThat(result.status()).isEqualTo(EvalCaseResultStatus.SKIP);
        assertThat(result.skipReason()).isEqualTo(EvalCaseScorer.SIMULATION_DEFERRED);
        assertThat(result.status()).isNotEqualTo(EvalCaseResultStatus.PASS);
    }

    @Test
    void shadowMustNotUseRecordedFishOnAsPassForDifferentAction() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.SHADOW_REPLAY,
                List.of(GuidanceAction.MOVE, GuidanceAction.STAY),
                List.of(),
                null,
                GuidanceAction.STAY,
                null,
                OutcomeKind.FISH_ON,
                List.of()
        );
        EvalCaseResult different = EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.MOVE));
        assertThat(different.status()).isEqualTo(EvalCaseResultStatus.UNSCORABLE);
        assertThat(different.skipReason()).isEqualTo(EvalCaseScorer.ACTION_DIFFERENT_OUTCOME_UNSCORABLE);
        assertThat(different.errorMessage()).isNull();
        assertThat(different.recordedOutcomeKind()).isEqualTo(OutcomeKind.FISH_ON);
        assertThat(different.actualPrimaryAction()).isEqualTo(GuidanceAction.MOVE);

        EvalCaseResult sameAction = EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.STAY));
        assertThat(sameAction.status()).isEqualTo(EvalCaseResultStatus.PASS);
    }

    @Test
    void differentMoveTargetIsUnscorableEvenWithFishOn() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.SHADOW_REPLAY,
                List.of(GuidanceAction.MOVE),
                List.of(),
                null,
                GuidanceAction.MOVE,
                TARGET_A,
                OutcomeKind.FISH_ON,
                List.of()
        );
        EvalCaseResult differentTarget = EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.MOVE, TARGET_B));
        assertThat(differentTarget.status()).isEqualTo(EvalCaseResultStatus.UNSCORABLE);
        assertThat(differentTarget.skipReason()).isEqualTo(EvalCaseScorer.ACTION_DIFFERENT_OUTCOME_UNSCORABLE);

        EvalCaseResult sameTarget = EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.MOVE, TARGET_A));
        assertThat(sameTarget.status()).isEqualTo(EvalCaseResultStatus.PASS);
    }

    @Test
    void versionCompareMarksDifferentActionUnscorableRegardlessOfKind() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.AGENT_POLICY_EVAL,
                List.of(GuidanceAction.MOVE, GuidanceAction.STAY),
                List.of(),
                null,
                GuidanceAction.STAY,
                null,
                OutcomeKind.NO_BITE,
                List.of()
        );
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.MOVE)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        EvalCaseResult compare = EvalCaseScorer.scoreVersionCompare(RUN, fixture, run(GuidanceAction.MOVE));
        assertThat(compare.status()).isEqualTo(EvalCaseResultStatus.UNSCORABLE);
        assertThat(compare.skipReason()).isEqualTo(EvalCaseScorer.ACTION_DIFFERENT_OUTCOME_UNSCORABLE);
    }

    @Test
    void policyRubricRejectsReturnWhenSafetyOk() {
        EvalCaseFixture fixture = fixture(
                EvalSuiteKind.AGENT_POLICY_EVAL,
                List.of(GuidanceAction.MOVE, GuidanceAction.STAY, GuidanceAction.CHANGE_LURE),
                List.of(GuidanceAction.RETURN),
                null,
                GuidanceAction.STAY,
                null,
                OutcomeKind.NO_BITE,
                List.of("MUST_NOT_RETURN_WHEN_SAFETY_OK", "NO_EXACT_PRIMARY_ACTION")
        );
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.MOVE)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(EvalCaseScorer.score(RUN, fixture, run(GuidanceAction.RETURN)).status())
                .isEqualTo(EvalCaseResultStatus.FAIL);
    }

    private static EvalCaseFixture fixture(
            EvalSuiteKind kind,
            List<GuidanceAction> allowed,
            List<GuidanceAction> forbidden,
            GuidanceAction exact,
            GuidanceAction recorded,
            UUID recordedTarget,
            OutcomeKind outcome,
            List<String> rubric
    ) {
        return new EvalCaseFixture(
                "case",
                kind,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                allowed,
                forbidden,
                List.of(),
                exact,
                rubric,
                null,
                null,
                recorded,
                recordedTarget,
                outcome,
                null,
                null,
                null
        );
    }

    private static AgentRunResult run(GuidanceAction action) {
        return run(action, null);
    }

    private static AgentRunResult run(GuidanceAction action, UUID targetTripWaypointId) {
        DeliveredDecision delivered = new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                RUN,
                action,
                null,
                targetTripWaypointId,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of(),
                "test",
                List.of(),
                false,
                null,
                1.0
        );
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                RUN,
                AgentRunStatus.COMPLETED,
                null,
                List.of(),
                null,
                null,
                delivered
        );
    }
}
