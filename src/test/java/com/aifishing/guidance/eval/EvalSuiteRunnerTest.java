package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalRun;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.runtime.DeterministicModelTurnClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvalSuiteRunnerTest {

    @Test
    void persistsReplayModeVersionsAndDoesNotScoreShadowOnRecordedFishOn() {
        InMemoryEvalRunStore store = new InMemoryEvalRunStore();
        EvalCaseFixture shadow = new EvalCaseFixture(
                "shadow/stay-vs-move",
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(GuidanceAction.STAY, GuidanceAction.MOVE),
                List.of(),
                List.of(),
                null,
                List.of(),
                EvalFixtures.shadowStayFishOn(),
                null,
                GuidanceAction.STAY,
                null,
                OutcomeKind.FISH_ON,
                "shadow-counterfactual",
                7L,
                null
        );
        DeterministicModelTurnClient model = new DeterministicModelTurnClient();
        EvalSuiteRunner runner = new EvalSuiteRunner(
                new CountingEvalRuntime(GuidanceAction.MOVE),
                runId -> Optional.empty(),
                store,
                model,
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        EvalRun evalRun = runner.run(new EvalSuiteRequest(
                UUID.fromString("cccccccc-0001-4000-8000-000000000001"),
                "shadow",
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(RecomputedComponent.PROMPT, RecomputedComponent.MODEL),
                EvalFixtures.versions(),
                null,
                List.of(shadow),
                "shadow-counterfactual",
                7L,
                null,
                List.of("shadow")
        ));

        assertThat(evalRun.replayMode()).isEqualTo(ReplayMode.FROZEN_REPLAY);
        assertThat(evalRun.recomputedComponents()).containsExactly(
                RecomputedComponent.PROMPT, RecomputedComponent.MODEL);
        assertThat(evalRun.componentVersions().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(evalRun.componentVersions().modelName()).isEqualTo("deterministic");
        List<EvalCaseResult> results = store.findResults(evalRun.evalRunId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().status()).isEqualTo(EvalCaseResultStatus.UNSCORABLE);
        assertThat(results.getFirst().skipReason()).isEqualTo(EvalCaseScorer.ACTION_DIFFERENT_OUTCOME_UNSCORABLE);
        assertThat(evalRun.coverage().loadedCases()).isEqualTo(1);
        assertThat(evalRun.coverage().passedCases()).isZero();
        assertThat(results.getFirst().recordedOutcomeKind()).isEqualTo(OutcomeKind.FISH_ON);
        assertThat(results.getFirst().actualPrimaryAction()).isEqualTo(GuidanceAction.MOVE);
    }

    @Test
    void missingSnapshotIsSkipNotPass() {
        InMemoryEvalRunStore store = new InMemoryEvalRunStore();
        EvalCaseFixture missing = new EvalCaseFixture(
                "missing/snapshot",
                EvalSuiteKind.PLATFORM_REGRESSION,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(GuidanceAction.STAY),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                UUID.fromString("dddddddd-0001-4000-8000-000000000001"),
                GuidanceAction.STAY,
                null,
                null,
                null,
                null,
                null
        );
        EvalSuiteRunner runner = new EvalSuiteRunner(
                new CountingEvalRuntime(GuidanceAction.STAY),
                runId -> Optional.empty(),
                store,
                new DeterministicModelTurnClient(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        EvalRun evalRun = runner.run(new EvalSuiteRequest(
                null,
                "platform",
                EvalSuiteKind.PLATFORM_REGRESSION,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                EvalFixtures.versions(),
                null,
                List.of(missing),
                null,
                null,
                null,
                null
        ));
        assertThat(evalRun.coverage().skippedCases()).isEqualTo(1);
        assertThat(evalRun.coverage().loadedCases()).isZero();
        assertThat(evalRun.coverage().passedCases()).isZero();
        assertThat(store.findResults(evalRun.evalRunId()).getFirst().status())
                .isEqualTo(EvalCaseResultStatus.SKIP);
        assertThat(store.findResults(evalRun.evalRunId()).getFirst().skipReason())
                .isEqualTo(EvalCaseScorer.SNAPSHOT_NOT_FOUND);
    }

    private static final class CountingEvalRuntime implements com.aifishing.guidance.spi.EvalRuntime {
        private final GuidanceAction action;

        private CountingEvalRuntime(GuidanceAction action) {
            this.action = action;
        }

        @Override
        public com.aifishing.guidance.contracts.AgentRunResult execute(
                com.aifishing.guidance.contracts.FrozenAgentRunSnapshot snapshot,
                ReplayMode replayMode
        ) {
            return new com.aifishing.guidance.contracts.AgentRunResult(
                    com.aifishing.guidance.contracts.GuidanceSchemaVersion.VALUE,
                    snapshot.runId(),
                    com.aifishing.guidance.contracts.AgentRunStatus.COMPLETED,
                    null,
                    List.of(),
                    null,
                    null,
                    new com.aifishing.guidance.contracts.DeliveredDecision(
                            com.aifishing.guidance.contracts.GuidanceSchemaVersion.VALUE,
                            snapshot.runId(),
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
                            "shadow",
                            List.of(),
                            false,
                            null,
                            1.0
                    )
            );
        }
    }
}
