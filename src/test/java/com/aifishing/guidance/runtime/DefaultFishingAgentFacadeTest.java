package com.aifishing.guidance.runtime;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.persist.InMemoryDecisionPersistence;
import com.aifishing.guidance.safety.DefaultSafetyRuleEngine;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.InMemoryAgentRuntimeControlStore;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.guidance.tools.AgentToolExecutor;
import com.aifishing.guidance.tools.InMemoryAgentToolRegistry;
import com.aifishing.guidance.validate.DefaultDecisionValidator;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import com.aifishing.guidance.versions.AgentPolicyResolver;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import com.aifishing.guidance.versions.ContextProfile;
import com.aifishing.guidance.versions.LearningProfile;
import com.aifishing.guidance.versions.ModelProfile;
import com.aifishing.guidance.versions.PromptProfile;
import com.aifishing.guidance.versions.ResolvedAgentPolicy;
import com.aifishing.guidance.versions.ToolsetProfile;
import com.aifishing.guidance.empirical.EmpiricalAlgorithm;
import com.aifishing.planning.PlanningProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_1;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_3;
import static com.aifishing.guidance.GuidancePhase2Fixtures.safeState;
import static com.aifishing.guidance.GuidancePhase2Fixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultFishingAgentFacadeTest {

    private InMemoryDecisionPersistence persistence;
    private GuidanceProperties properties;
    private GuidanceMetrics metrics;
    private EnvironmentSnapshotResolver resolver;
    private FishingSessionStateBuilder stateBuilder;
    private InMemoryAgentRuntimeControlStore controlStore;

    @BeforeEach
    void setUp() {
        persistence = new InMemoryDecisionPersistence();
        properties = deterministicProperties();
        metrics = new GuidanceMetrics(new SimpleMeterRegistry());
        resolver = sessionId -> new EnvironmentSnapshot(GuidancePhase2Fixtures.AT, null);
        stateBuilder = (sessionId, environment) -> safeState();
        controlStore = new InMemoryAgentRuntimeControlStore();
    }

    @Test
    void runningIsVisibleBeforeModelReturns() throws Exception {
        CountDownLatch enteredModel = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        AtomicInteger modelCalls = new AtomicInteger();
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                modelCalls.incrementAndGet();
                enteredModel.countDown();
                try {
                    if (!releaseModel.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("model was not released");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return super.nextTurn(input);
            }
        };
        DefaultFishingAgentFacade facade = facade(model);

        Thread worker = new Thread(() -> facade.run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null));
        worker.start();
        assertThat(enteredModel.await(3, TimeUnit.SECONDS)).isTrue();
        InMemoryDecisionPersistence.RunRow running = persistence.latest().orElseThrow();
        assertThat(running.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(persistence.statusLog()).contains(AgentRunStatus.RUNNING);
        assertThat(modelCalls.get()).isEqualTo(1);

        releaseModel.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(3));
        assertThat(worker.isAlive()).isFalse();
        assertThat(persistence.status(running.runId())).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(persistence.current(SESSION_ID)).isPresent();
        assertThat(persistence.current(SESSION_ID).orElseThrow().fallbackUsed()).isFalse();
    }

    @Test
    void blockDeliversPrescribedActionWithoutCallingModel() {
        AtomicBoolean modelCalled = new AtomicBoolean();
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                modelCalled.set(true);
                return super.nextTurn(input);
            }
        };
        stateBuilder = (sessionId, environment) -> state(
                WeatherCondition.THUNDERSTORM, 16.0, 4.5, 12_000.0, 1_500.0, 90,
                GuidancePhase2Fixtures.TRIP_WAYPOINT, List.of()
        );
        AgentRunResult result = facade(model).run(SESSION_ID, GuidanceTrigger.SAFETY_STATE_CHANGED, null);

        assertThat(modelCalled).isFalse();
        assertThat(result.status()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(result.candidate()).isNull();
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.RETURN);
        assertThat(result.delivered().fallbackUsed()).isTrue();
        assertThat(result.delivered().systemConfidence()).isNull();
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.SAFETY_BLOCK);
        assertThat(persistence.hasContext(result.runId())).isFalse();
        assertThat(persistence.current(SESSION_ID).orElseThrow()).isEqualTo(result.delivered());
        assertThat(persistence.current(SESSION_ID).orElseThrow().primaryAction())
                .isEqualTo(GuidanceAction.RETURN);
        assertThat(GuidanceContracts.schema("AgentRunResult").validate(
                GuidanceContracts.mapper().valueToTree(result)
        )).isEmpty();
    }

    @Test
    void currentReturnsDeliveredNotCandidateWhenValidationFails() {
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                return new ModelTurnResult.FinalCandidateDecision(GuidancePhase2Fixtures.moveCandidate());
            }
        };
        stateBuilder = (sessionId, environment) -> state(
                WeatherCondition.CLOUDY, 16.0, 80.0, 12_000.0, 1_500.0, 90,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, GuidancePhase2Fixtures.TRIP_WAYPOINT, null, true))
        );

        AgentRunResult result = facade(model).run(SESSION_ID, GuidanceTrigger.NO_BITE_THRESHOLD, null);
        DeliveredDecision current = persistence.current(SESSION_ID).orElseThrow();

        assertThat(result.status()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(result.candidate()).isNotNull();
        assertThat(result.candidate().primaryAction()).isEqualTo(GuidanceAction.MOVE);
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(result.delivered().fallbackUsed()).isTrue();
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.VALIDATION_FAILED);
        assertThat(current).isEqualTo(result.delivered());
        assertThat(current.primaryAction()).isNotEqualTo(result.candidate().primaryAction());
        assertThat(persistence.latest().orElseThrow().candidate()).isEqualTo(result.candidate());
    }

    @Test
    void crashBeforeDecisionFailsWithStayFallback() {
        resolver = sessionId -> {
            throw new IllegalStateException("weather down");
        };

        AgentRunResult result = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.candidate()).isNull();
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(result.delivered().fallbackUsed()).isTrue();
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.RUN_FAILED);
        assertThat(persistence.status(result.runId())).isEqualTo(AgentRunStatus.FAILED);
        assertThat(persistence.statusLog()).containsExactly(AgentRunStatus.RUNNING, AgentRunStatus.FAILED);
        assertThat(persistence.current(SESSION_ID).orElseThrow()).isEqualTo(result.delivered());
    }

    @Test
    void timeoutStopsDeterministicallyWithStayFallback() {
        properties.setRunTimeoutMs(80);
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return super.nextTurn(input);
            }
        };
        AgentRunResult result = facade(model).run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);

        assertThat(result.status()).isEqualTo(AgentRunStatus.TIMEOUT);
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(result.delivered().fallbackUsed()).isTrue();
        assertThat(result.delivered().systemConfidence()).isNull();
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.RUN_TIMEOUT);
        assertThat(persistence.status(result.runId())).isEqualTo(AgentRunStatus.TIMEOUT);
    }

    @Test
    void timeoutAfterFishHereStaysInPlaceWithoutForcingOriginalWaypoint() {
        properties.setRunTimeoutMs(80);
        stateBuilder = (sessionId, environment) -> fishHereState();
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return super.nextTurn(input);
            }
        };

        AgentRunResult result = facade(model).run(SESSION_ID, GuidanceTrigger.USER_STARTED_AD_HOC_FISHING, null);

        assertThat(result.status()).isEqualTo(AgentRunStatus.TIMEOUT);
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.RUN_TIMEOUT);
        assertThat(result.delivered().targetTripWaypointId()).isNull();
        assertThat(result.request().state().plan().originalPlanSteps())
                .extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);
        assertThat(result.request().state().fishing().activityStateSource())
                .isEqualTo(ActivityStateSource.USER_AD_HOC);
    }

    @Test
    void deterministicModeCompletesWithoutOpenAi() {
        ModelTurnClient model = ModelTurnClients.create(properties, null, null, Clock.systemUTC());
        assertThat(model).isInstanceOf(DeterministicModelTurnClient.class);

        AgentRunResult result = facade(model).run(SESSION_ID, GuidanceTrigger.NO_BITE_THRESHOLD, null);

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(result.delivered().fallbackUsed()).isFalse();
        assertThat(result.delivered().systemConfidence()).isNull();
        assertThat(result.request().modelProvider()).isEqualTo(DeterministicModelTurnClient.PROVIDER);
        assertThat(result.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(result.request().toolSchemaVersion()).isEqualTo("guidance-tools-v1");
        assertThat(result.request().contextVersion()).isEqualTo("guidance-context-v1");
        InMemoryDecisionPersistence.RunRow row = persistence.latest().orElseThrow();
        assertThat(row.metadata().agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(row.metadata().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(row.metadata().learningAlgorithmVersion())
                .isEqualTo(String.valueOf(EmpiricalAlgorithm.VERSION));
        assertThat(row.metadata().learningSnapshotVersion()).isNull();
        assertThat(GuidanceContracts.schema("AgentRunResult").validate(
                GuidanceContracts.mapper().valueToTree(result)
        )).isEmpty();
    }

    @Test
    void updateMetadataDoesNotRewriteBoundPolicyVersions() {
        AgentRunResult result = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);
        UUID runId = result.runId();
        persistence.updateMetadata(runId, new AgentRunMetadata(
                "other", "other-model", "v9", "other-prompt", "other-tools", "other-context",
                99, runId, "v9", "99", "snap-1"
        ));

        AgentRunMetadata metadata = persistence.get(runId).orElseThrow().metadata();
        assertThat(metadata.agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(metadata.promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(metadata.toolSchemaVersion()).isEqualTo("guidance-tools-v1");
        assertThat(metadata.contextVersion()).isEqualTo("guidance-context-v1");
        assertThat(metadata.modelProvider()).isEqualTo(DeterministicModelTurnClient.PROVIDER);
        assertThat(metadata.learningAlgorithmVersion()).isEqualTo(String.valueOf(EmpiricalAlgorithm.VERSION));
        assertThat(metadata.learningSnapshotVersion()).isNull();
        assertThat(metadata.decisionId()).isEqualTo(runId);
    }

    @Test
    void runtimeUsesBoundPromptInstructions() {
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                seen.set(input.promptInstructions());
                return super.nextTurn(input);
            }
        };
        DefaultFishingAgentRuntime runtime = runtime(model);
        ResolvedAgentPolicy policy = new ResolvedAgentPolicy(
                AgentPolicyVersion.V2,
                new PromptProfile("guidance-prompt-v1", "BOUND_PROMPT_INSTRUCTIONS"),
                new ContextProfile("guidance-context-v1", new TriggerClippedFishingAgentContextBuilder()),
                new ToolsetProfile("guidance-tools-v1", InMemoryAgentToolRegistry.empty()),
                ModelProfile.current(model),
                LearningProfile.empiricalAlgorithm1()
        );
        persistence.createRunning(
                GuidancePhase2Fixtures.SESSION_ID,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                "trace-bound",
                policy.toMetadata(null, GuidancePhase2Fixtures.SESSION_ID)
        );
        AgentRunResult result = runtime.execute(new AgentRunSnapshot(
                GuidancePhase2Fixtures.SESSION_ID,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                new EnvironmentSnapshot(GuidancePhase2Fixtures.AT, null),
                safeState(),
                null,
                null,
                "trace-bound",
                Clock.systemUTC().instant().plus(Duration.ofSeconds(20)),
                policy
        ));

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(seen.get()).isEqualTo("BOUND_PROMPT_INSTRUCTIONS");
        assertThat(result.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(persistence.get(GuidancePhase2Fixtures.SESSION_ID).orElseThrow().metadata()
                .agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V2);
    }

    @Test
    void killSwitchSkipsModelAndDoesNotPersistDeliveredAdvice() {
        controlStore.set(new AgentRuntimeControl(
                false, AgentPolicyVersion.V1, AgentPolicyVersion.V2, true, true,
                java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        AtomicBoolean modelCalled = new AtomicBoolean();
        AtomicInteger shadowCalls = new AtomicInteger();
        ModelTurnClient model = new DelegatingModelTurnClient(new DeterministicModelTurnClient()) {
            @Override
            public ModelTurnResult nextTurn(ModelTurnInput input) {
                modelCalled.set(true);
                return super.nextTurn(input);
            }
        };

        AgentRunResult result = facade(model, recordingEval(shadowCalls, null))
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);

        assertThat(modelCalled).isFalse();
        assertThat(shadowCalls.get()).isZero();
        assertThat(result.status()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(result.delivered().fallbackReason()).isEqualTo(GuidanceFallback.KILL_SWITCH);
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(GuidanceFallback.isKillSwitch(result)).isTrue();
        InMemoryDecisionPersistence.RunRow row = persistence.latest().orElseThrow();
        assertThat(row.status()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(row.delivered()).isNull();
        assertThat(row.fallbackReason()).isEqualTo(GuidanceFallback.KILL_SWITCH);
        assertThat(persistence.current(SESSION_ID)).isEmpty();
    }

    @Test
    void liveShadowRunsCandidateWhenEnabledAndSkipsWhenDisabled() {
        AtomicInteger shadowCalls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> shadowVersion = new java.util.concurrent.atomic.AtomicReference<>();
        DefaultFishingAgentFacade enabled = facade(
                new DeterministicModelTurnClient(),
                recordingEval(shadowCalls, shadowVersion)
        );
        controlStore.set(new AgentRuntimeControl(
                true, AgentPolicyVersion.V1, AgentPolicyVersion.V2, true, true,
                java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        enabled.run(SESSION_ID, GuidanceTrigger.NO_BITE_THRESHOLD, null);
        assertThat(shadowCalls.get()).isEqualTo(1);
        assertThat(shadowVersion.get()).isEqualTo(AgentPolicyVersion.V2);

        controlStore.set(new AgentRuntimeControl(
                true, AgentPolicyVersion.V1, AgentPolicyVersion.V2, false, true,
                java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        enabled.run(SESSION_ID, GuidanceTrigger.NO_BITE_THRESHOLD, null);
        assertThat(shadowCalls.get()).isEqualTo(1);
    }

    @Test
    void rollbackBindsNewRunsToPatchedProductionVersion() {
        AgentRunResult first = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);
        assertThat(persistence.get(first.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V1);

        controlStore.update(new com.aifishing.guidance.control.AgentRuntimeControlPatch(
                null, AgentPolicyVersion.V2, null, null, null
        ));
        AgentRunResult second = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);

        assertThat(persistence.get(first.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V1);
        assertThat(persistence.get(second.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V2);
    }

    @Test
    void rollbackFromV2ToV1BindsNewRunsWithoutRewritingHistory() {
        controlStore.update(new com.aifishing.guidance.control.AgentRuntimeControlPatch(
                null, AgentPolicyVersion.V2, null, null, null
        ));
        AgentRunResult first = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);
        assertThat(persistence.get(first.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V2);

        controlStore.update(new com.aifishing.guidance.control.AgentRuntimeControlPatch(
                null, AgentPolicyVersion.V1, null, null, null
        ));
        AgentRunResult second = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);

        assertThat(persistence.get(first.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V2);
        assertThat(second.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(persistence.get(second.runId()).orElseThrow().metadata().agentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V1);
    }

    @Test
    void productionRunStampsRegistryProfilesNotGuidancePropertiesLabels() {
        properties.setPromptVersion("other-prompt");
        properties.setToolSchemaVersion("other-tools");
        properties.setContextVersion("other-context");

        AgentRunResult result = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.NO_BITE_THRESHOLD, null);

        assertThat(result.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(result.request().toolSchemaVersion()).isEqualTo("guidance-tools-v1");
        assertThat(result.request().contextVersion()).isEqualTo("guidance-context-v1");
        InMemoryDecisionPersistence.RunRow row = persistence.get(result.runId()).orElseThrow();
        assertThat(row.metadata().agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(row.metadata().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(row.metadata().toolSchemaVersion()).isEqualTo("guidance-tools-v1");
        assertThat(row.metadata().contextVersion()).isEqualTo("guidance-context-v1");
        assertThat(row.metadata().learningAlgorithmVersion())
                .isEqualTo(String.valueOf(EmpiricalAlgorithm.VERSION));
        assertThat(row.metadata().learningSnapshotVersion()).isNull();
    }

    @Test
    void killSwitchDoesNotBlockFrozenEvalReplay() {
        controlStore.set(new AgentRuntimeControl(
                false, AgentPolicyVersion.V1, AgentPolicyVersion.V2, true, true,
                java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        AtomicInteger shadowCalls = new AtomicInteger();
        facade(new DeterministicModelTurnClient(), recordingEval(shadowCalls, null))
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);
        assertThat(shadowCalls.get()).isZero();

        AgentRunResult replay = evalRuntime().execute(
                com.aifishing.guidance.eval.EvalFixtures.deterministicStay(),
                com.aifishing.guidance.contracts.ReplayMode.FROZEN_REPLAY,
                AgentPolicyVersion.V2
        );
        assertThat(replay.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(replay.request().promptVersion()).isEqualTo("guidance-prompt-v1");
    }

    @Test
    void frozenReplayDoesNotMutateProductionRunOrSessionCurrent() {
        AgentRunResult live = facade(new DeterministicModelTurnClient())
                .run(SESSION_ID, GuidanceTrigger.USER_REQUEST, null);
        InMemoryDecisionPersistence.RunRow before = persistence.get(live.runId()).orElseThrow();
        DeliveredDecision current = persistence.current(SESSION_ID).orElseThrow();

        AgentRunResult v1 = evalRuntime().execute(
                freeze(live),
                com.aifishing.guidance.contracts.ReplayMode.FROZEN_REPLAY,
                AgentPolicyVersion.V1
        );
        AgentRunResult v2 = evalRuntime().execute(
                freeze(live),
                com.aifishing.guidance.contracts.ReplayMode.FROZEN_REPLAY,
                AgentPolicyVersion.V2
        );

        assertThat(v1.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(v2.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(v1.runId()).isNotEqualTo(live.runId());
        assertThat(v2.runId()).isNotEqualTo(live.runId());
        InMemoryDecisionPersistence.RunRow after = persistence.get(live.runId()).orElseThrow();
        assertThat(after.metadata().agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.delivered()).isEqualTo(before.delivered());
        assertThat(persistence.current(SESSION_ID).orElseThrow()).isEqualTo(current);
    }

    @Test
    void executeReplaysImmutableSnapshot() {
        ModelTurnClient model = new DeterministicModelTurnClient();
        DefaultFishingAgentRuntime runtime = runtime(model);
        persistence.createRunning(
                GuidancePhase2Fixtures.SESSION_ID,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                "trace-replay",
                new AgentRunMetadata(
                        model.provider(), model.modelName(), model.modelVersion(),
                        properties.getPromptVersion(), properties.getToolSchemaVersion(),
                        properties.getContextVersion(), 1, GuidancePhase2Fixtures.SESSION_ID
                )
        );
        AgentRunSnapshot snapshot = new AgentRunSnapshot(
                GuidancePhase2Fixtures.SESSION_ID,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                new EnvironmentSnapshot(GuidancePhase2Fixtures.AT, null),
                safeState(),
                null,
                null,
                "trace-replay",
                Clock.systemUTC().instant().plus(Duration.ofSeconds(20))
        );

        AgentRunResult result = runtime.execute(snapshot);

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.request().state()).isEqualTo(safeState());
        assertThat(result.delivered().fallbackUsed()).isFalse();
    }

    private DefaultFishingAgentFacade facade(ModelTurnClient model) {
        return facade(model, null);
    }

    private DefaultFishingAgentFacade facade(ModelTurnClient model, com.aifishing.guidance.spi.EvalRuntime evalRuntime) {
        AgentPolicyRegistry registry = new AgentPolicyRegistry(properties);
        AgentPolicyResolver policyResolver = new AgentPolicyResolver(
                registry,
                new TriggerClippedFishingAgentContextBuilder(),
                InMemoryAgentToolRegistry.empty(),
                model
        );
        if (evalRuntime == null) {
            return new DefaultFishingAgentFacade(
                    persistence,
                    resolver,
                    stateBuilder,
                    runtime(model),
                    model,
                    properties,
                    policyResolver,
                    controlStore,
                    metrics,
                    Clock.systemUTC()
            );
        }
        return new DefaultFishingAgentFacade(
                persistence,
                resolver,
                stateBuilder,
                runtime(model),
                model,
                properties,
                policyResolver,
                controlStore,
                evalRuntime,
                metrics,
                Clock.systemUTC()
        );
    }

    private static com.aifishing.guidance.spi.EvalRuntime recordingEval(
            AtomicInteger calls,
            java.util.concurrent.atomic.AtomicReference<String> version
    ) {
        return new com.aifishing.guidance.spi.EvalRuntime() {
            @Override
            public AgentRunResult execute(
                    com.aifishing.guidance.contracts.FrozenAgentRunSnapshot snapshot,
                    com.aifishing.guidance.contracts.ReplayMode replayMode
            ) {
                return execute(snapshot, replayMode, null);
            }

            @Override
            public AgentRunResult execute(
                    com.aifishing.guidance.contracts.FrozenAgentRunSnapshot snapshot,
                    com.aifishing.guidance.contracts.ReplayMode replayMode,
                    String policyVersion
            ) {
                calls.incrementAndGet();
                if (version != null) {
                    version.set(policyVersion);
                }
                return null;
            }
        };
    }

    private com.aifishing.guidance.eval.DefaultEvalRuntime evalRuntime() {
        AgentPolicyResolver policyResolver = new AgentPolicyResolver(
                new AgentPolicyRegistry(properties),
                new TriggerClippedFishingAgentContextBuilder(),
                InMemoryAgentToolRegistry.empty(),
                new DeterministicModelTurnClient()
        );
        return new com.aifishing.guidance.eval.DefaultEvalRuntime(
                new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties()),
                (sessionId, state, trigger) -> {
                    throw new AssertionError("live memory must not run in FROZEN_REPLAY");
                },
                new TriggerClippedFishingAgentContextBuilder(),
                new DeterministicModelTurnClient(),
                new DefaultDecisionValidator(new SessionProperties()),
                sessionId -> {
                    throw new AssertionError("live weather must not run in FROZEN_REPLAY");
                },
                (sessionId, environment) -> {
                    throw new AssertionError("live state rebuild");
                },
                properties,
                metrics,
                policyResolver
        );
    }

    private com.aifishing.guidance.contracts.FrozenAgentRunSnapshot freeze(AgentRunResult live) {
        InMemoryDecisionPersistence.RunRow row = persistence.get(live.runId()).orElseThrow();
        return new com.aifishing.guidance.contracts.FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                row.runId(),
                row.sessionId(),
                row.trigger(),
                row.state(),
                row.context(),
                RetrievedMemory.empty(),
                row.toolCalls(),
                java.time.Instant.parse("2026-09-18T14:00:00Z"),
                live.request() == null ? null : new com.aifishing.guidance.contracts.EvalComponentVersions(
                        live.request().promptVersion(),
                        live.request().modelProvider(),
                        live.request().modelName(),
                        live.request().modelVersion(),
                        live.request().toolSchemaVersion(),
                        live.request().contextVersion(),
                        null,
                        null,
                        null,
                        row.metadata().agentPolicyVersion(),
                        row.metadata().learningAlgorithmVersion(),
                        row.metadata().learningSnapshotVersion()
                )
        );
    }

    private DefaultFishingAgentRuntime runtime(ModelTurnClient model) {
        return new DefaultFishingAgentRuntime(
                new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties()),
                (sessionId, state, trigger) -> RetrievedMemory.empty(),
                new TriggerClippedFishingAgentContextBuilder(),
                model,
                new AgentToolExecutor(InMemoryAgentToolRegistry.empty(), properties, Clock.systemUTC()),
                new DefaultDecisionValidator(new SessionProperties()),
                persistence,
                properties,
                metrics,
                Clock.systemUTC()
        );
    }

    private static FishingSessionState fishHereState() {
        List<OriginalPlanStep> original = List.of(
                new OriginalPlanStep(1, SPOT_1, null, null, null, List.of(), "COMPLETED"),
                new OriginalPlanStep(2, SPOT_2, null, null, null, List.of(), "NAVIGATING"),
                new OriginalPlanStep(3, SPOT_3, null, null, null, List.of(), "UPCOMING")
        );
        FishingSessionState base = state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90,
                SPOT_2,
                List.of(new HorizonStep(1, GuidanceAction.STAY, null, 15, true)),
                original
        );
        FishingSessionState.Fishing fishing = base.fishing();
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                new FishingSessionState.Fishing(
                        SPOT_2,
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
                        UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8"),
                        GuidancePhase2Fixtures.AT,
                        null,
                        null,
                        null
                ),
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );
    }

    private static class DelegatingModelTurnClient implements ModelTurnClient {
        private final ModelTurnClient delegate;

        private DelegatingModelTurnClient(ModelTurnClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public String provider() {
            return delegate.provider();
        }

        @Override
        public String modelName() {
            return delegate.modelName();
        }

        @Override
        public String modelVersion() {
            return delegate.modelVersion();
        }

        @Override
        public ModelTurnResult nextTurn(ModelTurnInput input) {
            return delegate.nextTurn(input);
        }
    }

    private static GuidanceProperties deterministicProperties() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setRunTimeoutMs(5_000);
        properties.setModelCallTimeoutMs(1_000);
        properties.setMaxModelTurns(4);
        properties.setMaxToolRounds(2);
        properties.setMaxToolCalls(4);
        return properties;
    }
}
