package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Policy fixtures are nightly / opt-in. This test only checks fixture shape and
 * rubric scoring. It is not a required live-model quality gate.
 */
class AgentPolicyEvalFixtureTest {

    @Test
    void policyFixturesUseRubricNotExactPrimaryAction() {
        List<EvalCaseFixture> fixtures = EvalFixtureCatalog.policy();
        assertThat(fixtures).isNotEmpty();
        assertThat(fixtures).allMatch(fixture -> fixture.kind() == EvalSuiteKind.AGENT_POLICY_EVAL);
        assertThat(fixtures).allMatch(fixture -> fixture.exactPrimaryAction() == null);
        assertThat(fixtures).allMatch(fixture -> !fixture.rubric().isEmpty());
        assertThat(fixtures).allMatch(fixture -> fixture.allowedActions().size() > 1);
    }

    @Test
    void policyRubricScoresWithoutRequiringALiveModel() {
        EvalCaseFixture fixture = EvalFixtureCatalog.policy().getFirst();
        UUID evalRunId = UUID.fromString("bbbbbbbb-0001-4000-8000-000000000001");

        assertThat(EvalCaseScorer.score(evalRunId, fixture, decision(GuidanceAction.MOVE)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(EvalCaseScorer.score(evalRunId, fixture, decision(GuidanceAction.CHANGE_LURE)).status())
                .isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(EvalCaseScorer.score(evalRunId, fixture, decision(GuidanceAction.RETURN)).status())
                .isEqualTo(EvalCaseResultStatus.FAIL);
    }

    private static AgentRunResult decision(GuidanceAction action) {
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                UUID.fromString("bbbbbbbb-0001-4000-8000-000000000002"),
                AgentRunStatus.COMPLETED,
                null,
                List.of(),
                null,
                null,
                new DeliveredDecision(
                        GuidanceSchemaVersion.VALUE,
                        UUID.fromString("bbbbbbbb-0001-4000-8000-000000000002"),
                        action,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        15,
                        List.of(),
                        "policy-fixture",
                        List.of(),
                        false,
                        null,
                        0.7
                )
        );
    }
}
