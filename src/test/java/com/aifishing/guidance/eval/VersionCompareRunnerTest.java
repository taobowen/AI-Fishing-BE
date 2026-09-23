package com.aifishing.guidance.eval;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.eval.VersionCompareReport.ActionDifference;
import com.aifishing.guidance.eval.VersionCompareReport.VersionReplay;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.runtime.DeterministicModelTurnClient;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.safety.DefaultSafetyRuleEngine;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.tools.InMemoryAgentToolRegistry;
import com.aifishing.guidance.validate.DefaultDecisionValidator;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import com.aifishing.guidance.versions.AgentPolicyResolver;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import com.aifishing.planning.PlanningProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionCompareRunnerTest {

    private static final UUID EVAL_RUN = UUID.fromString("cccccccc-0002-4000-8000-000000000001");
    private static final UUID TARGET_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TARGET_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void v1AndV2AgreeOnFollowedStayAndCountFishOnAsSuccess() {
        InMemoryEvalRunStore store = new InMemoryEvalRunStore();
        VersionCompareRunner runner = new VersionCompareRunner(
                new ScriptedEvalRuntime(Map.of(
                        AgentPolicyVersion.V1, delivered(GuidanceAction.STAY, null),
                        AgentPolicyVersion.V2, delivered(GuidanceAction.STAY, null)
                )),
                runId -> Optional.empty(),
                store,
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = runner.compare(request(List.of(shadowStayFishOn())));

        assertThat(report.replayMode()).isEqualTo(ReplayMode.FROZEN_REPLAY);
        assertThat(report.versions()).containsExactly(AgentPolicyVersion.V1, AgentPolicyVersion.V2);
        assertThat(report.scenariosEvaluated()).isEqualTo(1);
        assertThat(report.scenariosSkipped()).isZero();
        assertThat(report.scenariosUnscorable()).isZero();
        assertThat(report.agreementRate()).isEqualTo(1.0);
        assertThat(report.outcomeRates().comparableFollowedCases()).isEqualTo(2);
        assertThat(report.outcomeRates().successCount()).isEqualTo(2);
        assertThat(report.outcomeRates().failureCount()).isZero();
        assertThat(report.outcomeRates().successRate()).isEqualTo(1.0);
        assertThat(report.actionDifferences()).isEmpty();
        assertThat(report.replays()).extracting(VersionReplay::policyVersion)
                .containsExactly(AgentPolicyVersion.V1, AgentPolicyVersion.V2);
        assertThat(store.findResults(report.evalRunId())).extracting(result -> result.caseId())
                .containsExactly(
                        VersionCompareRunner.persistedCaseId("shadow/stay-vs-move", AgentPolicyVersion.V1),
                        VersionCompareRunner.persistedCaseId("shadow/stay-vs-move", AgentPolicyVersion.V2)
                );
        assertThat(store.find(report.evalRunId()).orElseThrow().replayMode())
                .isEqualTo(ReplayMode.FROZEN_REPLAY);
    }

    @Test
    void differentActionFromHistoricalFishOnIsUnscorableAndExcludedFromOutcomeRates() {
        VersionCompareRunner runner = new VersionCompareRunner(
                new ScriptedEvalRuntime(Map.of(
                        AgentPolicyVersion.V1, delivered(GuidanceAction.MOVE, TARGET_A),
                        AgentPolicyVersion.V2, delivered(GuidanceAction.MOVE, TARGET_A)
                )),
                runId -> Optional.empty(),
                new InMemoryEvalRunStore(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = runner.compare(request(List.of(shadowStayFishOn())));

        assertThat(report.scenariosEvaluated()).isEqualTo(1);
        assertThat(report.scenariosUnscorable()).isEqualTo(2);
        assertThat(report.agreementRate()).isEqualTo(1.0);
        assertThat(report.outcomeRates().comparableFollowedCases()).isZero();
        assertThat(report.outcomeRates().successCount()).isZero();
        assertThat(report.outcomeRates().failureCount()).isZero();
        assertThat(report.outcomeRates().successRate()).isNull();
        assertThat(report.replays()).extracting(replay -> replay.result().status())
                .containsOnly(EvalCaseResultStatus.UNSCORABLE);
        assertThat(report.replays()).extracting(replay -> replay.result().skipReason())
                .containsOnly(EvalCaseScorer.ACTION_DIFFERENT_OUTCOME_UNSCORABLE);
        assertThat(report.actionDifferences()).extracting(ActionDifference::leftLabel)
                .containsOnly(VersionCompareRunner.RECORDED_LABEL);
        assertThat(report.actionDifferences()).extracting(ActionDifference::leftAction)
                .containsOnly(GuidanceAction.STAY);
        assertThat(report.actionDifferences()).extracting(ActionDifference::rightAction)
                .containsOnly(GuidanceAction.MOVE);
    }

    @Test
    void disagreementAndDifferentMoveTargetFillActionTableAndSplitOutcomeRates() {
        EvalCaseFixture recordedMove = new EvalCaseFixture(
                "shadow/move-target",
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(GuidanceAction.MOVE, GuidanceAction.STAY),
                List.of(),
                List.of(),
                null,
                List.of(),
                EvalFixtures.shadowStayFishOn(),
                null,
                GuidanceAction.MOVE,
                TARGET_A,
                OutcomeKind.FISH_ON,
                "shadow-move",
                1L,
                null
        );
        VersionCompareRunner runner = new VersionCompareRunner(
                new ScriptedEvalRuntime(Map.of(
                        AgentPolicyVersion.V1, delivered(GuidanceAction.MOVE, TARGET_A),
                        AgentPolicyVersion.V2, delivered(GuidanceAction.MOVE, TARGET_B)
                )),
                runId -> Optional.empty(),
                new InMemoryEvalRunStore(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = runner.compare(request(List.of(recordedMove)));

        assertThat(report.agreementRate()).isZero();
        assertThat(report.scenariosUnscorable()).isEqualTo(1);
        assertThat(report.outcomeRates().comparableFollowedCases()).isEqualTo(1);
        assertThat(report.outcomeRates().successCount()).isEqualTo(1);
        assertThat(report.outcomeRates().failureCount()).isZero();
        assertThat(report.actionDifferences()).hasSize(2);
        assertThat(report.actionDifferences()).anySatisfy(diff -> {
            assertThat(diff.leftLabel()).isEqualTo(VersionCompareRunner.RECORDED_LABEL);
            assertThat(diff.rightLabel()).isEqualTo(AgentPolicyVersion.V2);
            assertThat(diff.leftTargetTripWaypointId()).isEqualTo(TARGET_A);
            assertThat(diff.rightTargetTripWaypointId()).isEqualTo(TARGET_B);
        });
        assertThat(report.actionDifferences()).anySatisfy(diff -> {
            assertThat(diff.leftLabel()).isEqualTo(AgentPolicyVersion.V1);
            assertThat(diff.rightLabel()).isEqualTo(AgentPolicyVersion.V2);
        });
    }

    @Test
    void simulationStaysSkippedAndMissingSnapshotIsSkipped() {
        EvalCaseFixture simulation = new EvalCaseFixture(
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
        EvalCaseFixture missing = new EvalCaseFixture(
                "missing/snapshot",
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(GuidanceAction.STAY),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                UUID.fromString("dddddddd-0002-4000-8000-000000000001"),
                GuidanceAction.STAY,
                null,
                OutcomeKind.FISH_ON,
                null,
                null,
                null
        );
        VersionCompareRunner runner = new VersionCompareRunner(
                new ScriptedEvalRuntime(Map.of()),
                runId -> Optional.empty(),
                new InMemoryEvalRunStore(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = runner.compare(request(List.of(simulation, missing)));

        assertThat(report.scenariosEvaluated()).isZero();
        assertThat(report.scenariosSkipped()).isEqualTo(2);
        assertThat(report.scenariosUnscorable()).isZero();
        assertThat(report.replays()).isEmpty();
        assertThat(report.agreementRate()).isNull();
        assertThat(report.outcomeRates().comparableFollowedCases()).isZero();
    }

    @Test
    void recordsValidationSafetyAndInvalidMoveCounters() {
        DecisionValidationResult invalidMove = new DecisionValidationResult(
                GuidanceSchemaVersion.VALUE,
                false,
                List.of(new ValidationIssue(DecisionValidationCheck.WAYPOINT_NOT_FOUND, "missing"))
        );
        DecisionValidationResult oscillation = new DecisionValidationResult(
                GuidanceSchemaVersion.VALUE,
                false,
                List.of(
                        new ValidationIssue(DecisionValidationCheck.MOVE_OSCILLATION, "osc"),
                        new ValidationIssue(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN, "cool")
                )
        );
        VersionCompareRunner runner = new VersionCompareRunner(
                new ScriptedEvalRuntime(
                        Map.of(
                                AgentPolicyVersion.V1, fallbackSafety(GuidanceAction.RETURN),
                                AgentPolicyVersion.V2, delivered(GuidanceAction.STAY, null)
                        ),
                        Map.of(
                                AgentPolicyVersion.V1, candidate(GuidanceAction.MOVE, TARGET_A),
                                AgentPolicyVersion.V2, candidate(GuidanceAction.MOVE, TARGET_A)
                        ),
                        Map.of(
                                AgentPolicyVersion.V1, invalidMove,
                                AgentPolicyVersion.V2, oscillation
                        )
                ),
                runId -> Optional.empty(),
                new InMemoryEvalRunStore(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = runner.compare(request(List.of(shadowStayFishOn())));

        assertThat(report.safetyBlockCount()).isEqualTo(1);
        assertThat(report.invalidMoveCount()).isEqualTo(1);
        assertThat(report.moveOscillationCount()).isEqualTo(1);
        assertThat(report.opportunityInCooldownCount()).isEqualTo(1);
        assertThat(report.validationPassRate()).isZero();
    }

    @Test
    void evalSuiteRunnerCompareUsesFrozenReplayAndDoesNotTouchLiveStores() {
        AtomicInteger weatherCalls = new AtomicInteger();
        AtomicInteger memoryCalls = new AtomicInteger();
        InMemoryEvalRunStore store = new InMemoryEvalRunStore();
        EvalSuiteRunner suite = new EvalSuiteRunner(
                liveRuntime(memoryCalls, weatherCalls),
                runId -> Optional.empty(),
                store,
                new DeterministicModelTurnClient(),
                Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC)
        );

        VersionCompareReport report = suite.compare(request(List.of(shadowStayFishOn(), simulation())));

        assertThat(weatherCalls.get()).isZero();
        assertThat(memoryCalls.get()).isZero();
        assertThat(report.scenariosEvaluated()).isEqualTo(1);
        assertThat(report.scenariosSkipped()).isEqualTo(1);
        assertThat(report.agreementRate()).isEqualTo(1.0);
        assertThat(report.replays()).allSatisfy(replay -> {
            assertThat(replay.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
            assertThat(replay.result().status()).isEqualTo(EvalCaseResultStatus.PASS);
        });
        assertThat(report.outcomeRates().comparableFollowedCases()).isEqualTo(2);
        assertThat(report.outcomeRates().successCount()).isEqualTo(2);
    }

    @Test
    void defaultEvalRuntimeResolvesCompareVersionAndRejectsUnknownCatalogId() {
        DefaultEvalRuntime runtime = liveRuntime(new AtomicInteger(), new AtomicInteger());
        FrozenAgentRunSnapshot snapshot = EvalFixtures.shadowStayFishOn();

        AgentRunResult v2 = runtime.execute(snapshot, ReplayMode.FROZEN_REPLAY, AgentPolicyVersion.V2);
        assertThat(v2.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(v2.request().promptVersion()).isEqualTo("guidance-prompt-v1");

        assertThatThrownBy(() -> runtime.execute(snapshot, ReplayMode.FROZEN_REPLAY, "v9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    private static EvalSuiteRequest request(List<EvalCaseFixture> cases) {
        return new EvalSuiteRequest(
                EVAL_RUN,
                "version-compare",
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.COMPONENT_RECOMPUTE,
                List.of(),
                EvalFixtures.versions(),
                null,
                cases,
                "version-compare",
                1L,
                null,
                List.of("compare")
        );
    }

    private static EvalCaseFixture shadowStayFishOn() {
        return new EvalCaseFixture(
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
    }

    private static EvalCaseFixture simulation() {
        return new EvalCaseFixture(
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
    }

    private static DefaultEvalRuntime liveRuntime(AtomicInteger memoryCalls, AtomicInteger weatherCalls) {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setRunTimeoutMs(5_000);
        DeterministicModelTurnClient model = new DeterministicModelTurnClient();
        TriggerClippedFishingAgentContextBuilder context = new TriggerClippedFishingAgentContextBuilder();
        AgentPolicyResolver resolver = new AgentPolicyResolver(
                new AgentPolicyRegistry(properties),
                context,
                InMemoryAgentToolRegistry.empty(),
                model
        );
        return new DefaultEvalRuntime(
                new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties()),
                (sessionId, state, trigger) -> {
                    memoryCalls.incrementAndGet();
                    throw new AssertionError("live memory must not run in FROZEN_REPLAY");
                },
                context,
                model,
                new DefaultDecisionValidator(new SessionProperties()),
                sessionId -> {
                    weatherCalls.incrementAndGet();
                    throw new AssertionError("live weather must not run in FROZEN_REPLAY");
                },
                (sessionId, environment) -> {
                    throw new AssertionError("live state rebuild");
                },
                properties,
                new GuidanceMetrics(new SimpleMeterRegistry()),
                resolver
        );
    }

    private static DeliveredDecision delivered(GuidanceAction action, UUID target) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                EVAL_RUN,
                action,
                null,
                target,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of(),
                "compare",
                List.of(),
                false,
                null,
                1.0
        );
    }

    private static DeliveredDecision fallbackSafety(GuidanceAction action) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                EVAL_RUN,
                action,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of(GuidanceFallback.SAFETY_BLOCK),
                "safety",
                List.of(),
                true,
                GuidanceFallback.SAFETY_BLOCK,
                1.0
        );
    }

    private static CandidateDecision candidate(GuidanceAction action, UUID target) {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                EVAL_RUN,
                action,
                null,
                target,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of(),
                "candidate",
                List.of(),
                1.0
        );
    }

    private static final class ScriptedEvalRuntime implements com.aifishing.guidance.spi.EvalRuntime {
        private final Map<String, DeliveredDecision> delivered;
        private final Map<String, CandidateDecision> candidates;
        private final Map<String, DecisionValidationResult> validations;

        private ScriptedEvalRuntime(Map<String, DeliveredDecision> delivered) {
            this(delivered, Map.of(), Map.of());
        }

        private ScriptedEvalRuntime(
                Map<String, DeliveredDecision> delivered,
                Map<String, CandidateDecision> candidates,
                Map<String, DecisionValidationResult> validations
        ) {
            this.delivered = delivered;
            this.candidates = candidates;
            this.validations = validations;
        }

        @Override
        public AgentRunResult execute(FrozenAgentRunSnapshot snapshot, ReplayMode replayMode) {
            return execute(snapshot, replayMode, AgentPolicyVersion.V1);
        }

        @Override
        public AgentRunResult execute(FrozenAgentRunSnapshot snapshot, ReplayMode replayMode, String policyVersion) {
            assertThat(replayMode).isEqualTo(ReplayMode.FROZEN_REPLAY);
            String version = policyVersion == null ? AgentPolicyVersion.V1 : policyVersion;
            return new AgentRunResult(
                    GuidanceSchemaVersion.VALUE,
                    snapshot.runId(),
                    AgentRunStatus.COMPLETED,
                    null,
                    List.of(),
                    candidates.get(version),
                    validations.get(version),
                    delivered.get(version)
            );
        }
    }
}
