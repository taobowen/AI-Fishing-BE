package com.aifishing.guidance.eval;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.EvalExecutionMode;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.runtime.AgentRunMetadata;
import com.aifishing.guidance.runtime.AgentRunSnapshot;
import com.aifishing.guidance.runtime.DefaultFishingAgentRuntime;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.runtime.ModelTurnClient;
import com.aifishing.guidance.spi.DecisionValidator;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.guidance.spi.EvalRuntime;
import com.aifishing.guidance.spi.FishingAgentContextBuilder;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.guidance.spi.MemoryRetrievalService;
import com.aifishing.guidance.spi.SafetyRuleEngine;
import com.aifishing.guidance.tools.AgentToolExecutor;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import com.aifishing.guidance.versions.AgentPolicyResolver;
import com.aifishing.guidance.versions.ResolvedAgentPolicy;
import com.aifishing.guidance.versions.ToolsetProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * EVAL-mode replay beside the live Facade. Uses a read-only tool registry and
 * in-memory audit only. Does not write delivered decisions, enqueue triggers,
 * or update memory / empirical stores.
 */
@Component
public class DefaultEvalRuntime implements EvalRuntime {

    private static final Set<RecomputedComponent> LIVE_DATA_COMPONENTS = Set.of(
            RecomputedComponent.MEMORY_RETRIEVAL,
            RecomputedComponent.TOOLS,
            RecomputedComponent.CONTEXT,
            RecomputedComponent.ENVIRONMENT
    );

    private final SafetyRuleEngine safetyRuleEngine;
    private final MemoryRetrievalService liveMemory;
    private final FishingAgentContextBuilder contextBuilder;
    private final ModelTurnClient modelTurnClient;
    private final DecisionValidator decisionValidator;
    private final EnvironmentSnapshotResolver environmentResolver;
    private final FishingSessionStateBuilder stateBuilder;
    private final GuidanceProperties properties;
    private final GuidanceMetrics metrics;
    private final List<RecomputedComponent> recomputedComponents;
    private final AgentPolicyResolver policyResolver;

    @Autowired
    public DefaultEvalRuntime(
            SafetyRuleEngine safetyRuleEngine,
            MemoryRetrievalService liveMemory,
            FishingAgentContextBuilder contextBuilder,
            ModelTurnClient modelTurnClient,
            DecisionValidator decisionValidator,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            GuidanceProperties properties,
            GuidanceMetrics metrics,
            AgentPolicyResolver policyResolver
    ) {
        this(
                safetyRuleEngine,
                liveMemory,
                contextBuilder,
                modelTurnClient,
                decisionValidator,
                environmentResolver,
                stateBuilder,
                properties,
                metrics,
                List.of(),
                policyResolver
        );
    }

    public DefaultEvalRuntime(
            SafetyRuleEngine safetyRuleEngine,
            MemoryRetrievalService liveMemory,
            FishingAgentContextBuilder contextBuilder,
            ModelTurnClient modelTurnClient,
            DecisionValidator decisionValidator,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            GuidanceProperties properties,
            GuidanceMetrics metrics
    ) {
        this(
                safetyRuleEngine,
                liveMemory,
                contextBuilder,
                modelTurnClient,
                decisionValidator,
                environmentResolver,
                stateBuilder,
                properties,
                metrics,
                List.of(),
                null
        );
    }

    DefaultEvalRuntime(
            SafetyRuleEngine safetyRuleEngine,
            MemoryRetrievalService liveMemory,
            FishingAgentContextBuilder contextBuilder,
            ModelTurnClient modelTurnClient,
            DecisionValidator decisionValidator,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            GuidanceProperties properties,
            GuidanceMetrics metrics,
            List<RecomputedComponent> recomputedComponents
    ) {
        this(
                safetyRuleEngine,
                liveMemory,
                contextBuilder,
                modelTurnClient,
                decisionValidator,
                environmentResolver,
                stateBuilder,
                properties,
                metrics,
                recomputedComponents,
                null
        );
    }

    DefaultEvalRuntime(
            SafetyRuleEngine safetyRuleEngine,
            MemoryRetrievalService liveMemory,
            FishingAgentContextBuilder contextBuilder,
            ModelTurnClient modelTurnClient,
            DecisionValidator decisionValidator,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            GuidanceProperties properties,
            GuidanceMetrics metrics,
            List<RecomputedComponent> recomputedComponents,
            AgentPolicyResolver policyResolver
    ) {
        this.safetyRuleEngine = safetyRuleEngine;
        this.liveMemory = liveMemory;
        this.contextBuilder = contextBuilder;
        this.modelTurnClient = modelTurnClient;
        this.decisionValidator = decisionValidator;
        this.environmentResolver = environmentResolver;
        this.stateBuilder = stateBuilder;
        this.properties = properties;
        this.metrics = metrics;
        this.recomputedComponents = List.copyOf(recomputedComponents == null ? List.of() : recomputedComponents);
        this.policyResolver = policyResolver;
    }

    public DefaultEvalRuntime withRecomputed(List<RecomputedComponent> components) {
        return new DefaultEvalRuntime(
                safetyRuleEngine,
                liveMemory,
                contextBuilder,
                modelTurnClient,
                decisionValidator,
                environmentResolver,
                stateBuilder,
                properties,
                metrics,
                components,
                policyResolver
        );
    }

    @Override
    public EvalExecutionMode executionMode() {
        return EvalExecutionMode.EVAL;
    }

    @Override
    public AgentRunResult execute(FrozenAgentRunSnapshot snapshot, ReplayMode replayMode) {
        return execute(snapshot, replayMode, AgentPolicyVersion.V1);
    }

    @Override
    public AgentRunResult execute(FrozenAgentRunSnapshot snapshot, ReplayMode replayMode, String policyVersion) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(replayMode, "replayMode");
        if (replayMode == ReplayMode.COMPONENT_RECOMPUTE && recomputedComponents.isEmpty()) {
            throw new IllegalArgumentException("COMPONENT_RECOMPUTE must list recomputedComponents");
        }
        if (replayMode == ReplayMode.FROZEN_REPLAY && mixesLiveData(recomputedComponents)) {
            throw new IllegalArgumentException("FROZEN_REPLAY must not recompute live weather, memory, tools, or context");
        }

        Clock clock = Clock.fixed(snapshot.recordedClock(), ZoneOffset.UTC);
        boolean recomputeMemory = replayMode == ReplayMode.COMPONENT_RECOMPUTE
                && recomputedComponents.contains(RecomputedComponent.MEMORY_RETRIEVAL);
        boolean recomputeContext = replayMode == ReplayMode.COMPONENT_RECOMPUTE
                && recomputedComponents.contains(RecomputedComponent.CONTEXT);
        boolean recomputeEnvironment = replayMode == ReplayMode.COMPONENT_RECOMPUTE
                && recomputedComponents.contains(RecomputedComponent.ENVIRONMENT);
        boolean recomputeTools = replayMode == ReplayMode.COMPONENT_RECOMPUTE
                && recomputedComponents.contains(RecomputedComponent.TOOLS);

        FishingSessionState state = snapshot.state();
        EnvironmentSnapshot environment = new EnvironmentSnapshot(snapshot.recordedClock(), null);
        if (recomputeEnvironment) {
            environment = environmentResolver.resolve(snapshot.sessionId());
            state = stateBuilder.build(snapshot.sessionId(), environment);
        }

        RetrievedMemory frozenMemory = snapshot.retrievedMemory() == null
                ? RetrievedMemory.empty()
                : snapshot.retrievedMemory();
        RetrievedMemory memory = recomputeMemory
                ? liveMemory.retrieve(snapshot.sessionId(), state, snapshot.trigger())
                : frozenMemory;
        MemoryRetrievalService memoryService = recomputeMemory
                ? liveMemory
                : (sessionId, ignoredState, trigger) -> frozenMemory;

        FishingAgentContext context = snapshot.context();
        if (recomputeContext || (recomputeMemory && context == null)) {
            context = contextBuilder.build(state, snapshot.trigger(), memory);
        }

        DefaultEvalToolRegistry tools = recomputeTools
                ? DefaultEvalToolRegistry.unknownOnly(clock)
                : DefaultEvalToolRegistry.recorded(snapshot.recordedToolObservations(), clock);

        ResolvedAgentPolicy policy = bindPolicy(tools, policyVersion);
        EvalOnlyDecisionPersistence persistence = new EvalOnlyDecisionPersistence();
        UUID evalRunId = UUID.randomUUID();
        persistence.createRunning(
                evalRunId,
                snapshot.sessionId(),
                snapshot.trigger(),
                "eval",
                policy == null
                        ? new AgentRunMetadata(
                                modelTurnClient.provider(),
                                modelTurnClient.modelName(),
                                modelTurnClient.modelVersion(),
                                properties.getPromptVersion(),
                                properties.getToolSchemaVersion(),
                                properties.getContextVersion(),
                                null,
                                evalRunId
                        )
                        : policy.toMetadata(null, evalRunId)
        );

        DefaultFishingAgentRuntime runtime = new DefaultFishingAgentRuntime(
                safetyRuleEngine,
                memoryService,
                contextBuilder,
                modelTurnClient,
                new AgentToolExecutor(tools, properties, clock),
                decisionValidator,
                persistence,
                properties,
                metrics,
                clock
        );
        return runtime.execute(new AgentRunSnapshot(
                evalRunId,
                snapshot.sessionId(),
                snapshot.trigger(),
                environment,
                state,
                context,
                null,
                "eval",
                clock.instant().plusMillis(Math.max(1L, properties.getRunTimeoutMs())),
                policy
        ));
    }

    /**
     * Resolve the requested catalog version and overlay the eval tool registry so
     * frozen replay uses recorded observations (not live {@code historical_performance}).
     * {@link VersionCompareRunner} passes each candidate ({@code v1}, {@code v2}).
     * Suite replay without a version argument binds catalog v1.
     */
    private ResolvedAgentPolicy bindPolicy(DefaultEvalToolRegistry evalTools, String policyVersion) {
        if (policyResolver == null) {
            return null;
        }
        String version = policyVersion == null || policyVersion.isBlank()
                ? AgentPolicyVersion.V1
                : policyVersion.trim();
        ResolvedAgentPolicy resolved = policyResolver.resolve(version);
        return resolved.withToolset(new ToolsetProfile(resolved.toolset().version(), evalTools));
    }

    static boolean mixesLiveData(List<RecomputedComponent> components) {
        if (components == null) {
            return false;
        }
        for (RecomputedComponent component : components) {
            if (LIVE_DATA_COMPONENTS.contains(component)) {
                return true;
            }
        }
        return false;
    }
}
