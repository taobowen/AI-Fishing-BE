package com.aifishing.guidance.eval;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.runtime.DeterministicModelTurnClient;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import com.aifishing.guidance.versions.AgentPolicyResolver;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.safety.DefaultSafetyRuleEngine;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.guidance.spi.MemoryRetrievalService;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.validate.DefaultDecisionValidator;
import com.aifishing.planning.PlanningProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEvalRuntimeTest {

    @Test
    void frozenReplayUsesRecordedStateAndDoesNotTouchLiveWeatherOrMemory() {
        AtomicInteger weatherCalls = new AtomicInteger();
        AtomicInteger memoryCalls = new AtomicInteger();
        DefaultEvalRuntime runtime = runtime(
                (sessionId, state, trigger) -> {
                    memoryCalls.incrementAndGet();
                    throw new AssertionError("live memory must not run in FROZEN_REPLAY");
                },
                sessionId -> {
                    weatherCalls.incrementAndGet();
                    throw new AssertionError("live weather must not run in FROZEN_REPLAY");
                }
        );

        AgentRunResult result = runtime.execute(EvalFixtures.safetyBlock(), ReplayMode.FROZEN_REPLAY);

        assertThat(weatherCalls.get()).isZero();
        assertThat(memoryCalls.get()).isZero();
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.RETURN);
        assertThat(result.delivered().fallbackUsed()).isTrue();
    }

    @Test
    void frozenReplayRejectsLiveDataRecomputeList() {
        DefaultEvalRuntime runtime = runtime().withRecomputed(List.of(RecomputedComponent.MEMORY_RETRIEVAL));
        assertThatThrownBy(() -> runtime.execute(EvalFixtures.deterministicStay(), ReplayMode.FROZEN_REPLAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FROZEN_REPLAY");
    }

    @Test
    void componentRecomputeRequiresListedComponents() {
        assertThatThrownBy(() -> runtime().execute(EvalFixtures.deterministicStay(), ReplayMode.COMPONENT_RECOMPUTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recomputedComponents");
    }

    @Test
    void componentRecomputeMemoryCallsLiveRetrievalOnly() {
        AtomicInteger memoryCalls = new AtomicInteger();
        AtomicInteger weatherCalls = new AtomicInteger();
        RetrievedMemory live = new RetrievedMemory(null, List.of(), List.of("live-memory"));
        DefaultEvalRuntime runtime = runtime(
                (sessionId, state, trigger) -> {
                    memoryCalls.incrementAndGet();
                    return live;
                },
                sessionId -> {
                    weatherCalls.incrementAndGet();
                    return new EnvironmentSnapshot(EvalFixtures.CLOCK, null);
                }
        ).withRecomputed(List.of(RecomputedComponent.MEMORY_RETRIEVAL));

        AgentRunResult result = runtime.execute(EvalFixtures.deterministicStay(), ReplayMode.COMPONENT_RECOMPUTE);

        assertThat(memoryCalls.get()).isEqualTo(1);
        assertThat(weatherCalls.get()).isZero();
        assertThat(result.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
    }

    @Test
    void v1AndV2EvaluateTheSameFrozenStateIndependently() {
        DefaultEvalRuntime runtime = runtimeWithPolicy();
        FrozenAgentRunSnapshot snapshot = EvalFixtures.deterministicStay();

        AgentRunResult v1 = runtime.execute(snapshot, ReplayMode.FROZEN_REPLAY, AgentPolicyVersion.V1);
        AgentRunResult v2 = runtime.execute(snapshot, ReplayMode.FROZEN_REPLAY, AgentPolicyVersion.V2);

        assertThat(v1.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(v2.delivered().primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(v1.runId()).isNotEqualTo(v2.runId());
        assertThat(v1.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(v2.request().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(snapshot.state()).isEqualTo(EvalFixtures.deterministicStay().state());
    }

    @Test
    void evalModeDoesNotExposeSessionDeliveredCurrent() {
        EvalOnlyDecisionPersistence persistence = new EvalOnlyDecisionPersistence();
        persistence.createRunning(
                EvalFixtures.RUN_STAY,
                EvalFixtures.SESSION_ID,
                com.aifishing.guidance.contracts.GuidanceTrigger.USER_REQUEST,
                "eval",
                null
        );
        persistence.finalize(
                EvalFixtures.RUN_STAY,
                com.aifishing.guidance.contracts.AgentRunStatus.COMPLETED,
                runtime().execute(EvalFixtures.deterministicStay(), ReplayMode.FROZEN_REPLAY).delivered()
        );
        assertThat(persistence.current(EvalFixtures.SESSION_ID)).isEmpty();
    }

    private static DefaultEvalRuntime runtimeWithPolicy() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setRunTimeoutMs(5_000);
        AgentPolicyResolver resolver = new AgentPolicyResolver(
                new AgentPolicyRegistry(properties),
                new TriggerClippedFishingAgentContextBuilder(),
                com.aifishing.guidance.tools.InMemoryAgentToolRegistry.empty(),
                new DeterministicModelTurnClient()
        );
        return new DefaultEvalRuntime(
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
                new GuidanceMetrics(new SimpleMeterRegistry()),
                resolver
        );
    }

    private static DefaultEvalRuntime runtime() {
        return runtime(
                (sessionId, state, trigger) -> {
                    throw new AssertionError("live memory");
                },
                sessionId -> {
                    throw new AssertionError("live weather");
                }
        );
    }

    private static DefaultEvalRuntime runtime(
            MemoryRetrievalService memory,
            EnvironmentSnapshotResolver weather
    ) {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setRunTimeoutMs(5_000);
        return new DefaultEvalRuntime(
                new DefaultSafetyRuleEngine(new PlanningProperties(), new SessionProperties()),
                memory,
                new TriggerClippedFishingAgentContextBuilder(),
                new DeterministicModelTurnClient(),
                new DefaultDecisionValidator(new SessionProperties()),
                weather,
                (sessionId, environment) -> {
                    throw new AssertionError("live state rebuild");
                },
                properties,
                new GuidanceMetrics(new SimpleMeterRegistry())
        );
    }
}
