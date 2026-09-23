package com.aifishing.guidance.eval;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalRun;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.runtime.DeterministicModelTurnClient;
import com.aifishing.guidance.safety.DefaultSafetyRuleEngine;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.validate.DefaultDecisionValidator;
import com.aifishing.planning.PlanningProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRegressionEvalTest {

    @Test
    void platformFixturesPassWithDeterministicModelAndSkipSimulation() {
        List<EvalCaseFixture> fixtures = EvalFixtureCatalog.platform();
        assertThat(fixtures).extracting(EvalCaseFixture::caseId).containsExactly(
                "platform/deterministic-stay",
                "platform/safety-block-return",
                "platform/simulation-deferred"
        );
        assertThat(fixtures.stream().filter(f -> f.exactPrimaryAction() != null))
                .allMatch(f -> f.allowedActions().size() == 1 && f.exactPrimaryAction() == GuidanceAction.RETURN);

        InMemoryEvalRunStore store = new InMemoryEvalRunStore();
        DeterministicModelTurnClient model = new DeterministicModelTurnClient();
        EvalSuiteRunner runner = new EvalSuiteRunner(
                runtime(),
                runId -> java.util.Optional.empty(),
                store,
                model,
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
                fixtures,
                null,
                null,
                null,
                List.of("platform")
        ));

        assertThat(evalRun.kind()).isEqualTo(EvalSuiteKind.PLATFORM_REGRESSION);
        assertThat(evalRun.replayMode()).isEqualTo(ReplayMode.FROZEN_REPLAY);
        assertThat(evalRun.componentVersions().modelProvider()).isEqualTo("deterministic");
        assertThat(evalRun.coverage().attemptedCases()).isEqualTo(3);
        assertThat(evalRun.coverage().loadedCases()).isEqualTo(2);
        assertThat(evalRun.coverage().skippedCases()).isEqualTo(1);
        assertThat(evalRun.coverage().passedCases()).isEqualTo(2);
        assertThat(evalRun.coverage().evalCoverage()).isEqualTo(2.0 / 3.0);

        List<EvalCaseResult> results = store.findResults(evalRun.evalRunId());
        assertThat(results).hasSize(3);
        assertThat(result(results, "platform/safety-block-return").status()).isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(result(results, "platform/safety-block-return").actualPrimaryAction())
                .isEqualTo(GuidanceAction.RETURN);
        assertThat(result(results, "platform/deterministic-stay").status()).isEqualTo(EvalCaseResultStatus.PASS);
        assertThat(result(results, "platform/deterministic-stay").actualPrimaryAction())
                .isEqualTo(GuidanceAction.STAY);
        assertThat(result(results, "platform/simulation-deferred").status()).isEqualTo(EvalCaseResultStatus.SKIP);
        assertThat(result(results, "platform/simulation-deferred").skipReason())
                .isEqualTo(EvalCaseScorer.SIMULATION_DEFERRED);
    }

    private static EvalCaseResult result(List<EvalCaseResult> results, String caseId) {
        return results.stream().filter(item -> caseId.equals(item.caseId())).findFirst().orElseThrow();
    }

    private static DefaultEvalRuntime runtime() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setRunTimeoutMs(5_000);
        return new DefaultEvalRuntime(
                new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties()),
                (sessionId, state, trigger) -> {
                    throw new AssertionError("live memory");
                },
                new TriggerClippedFishingAgentContextBuilder(),
                new DeterministicModelTurnClient(),
                new DefaultDecisionValidator(new SessionProperties()),
                sessionId -> {
                    throw new AssertionError("live weather");
                },
                (sessionId, environment) -> {
                    throw new AssertionError("live state");
                },
                properties,
                new GuidanceMetrics(new SimpleMeterRegistry())
        );
    }
}
