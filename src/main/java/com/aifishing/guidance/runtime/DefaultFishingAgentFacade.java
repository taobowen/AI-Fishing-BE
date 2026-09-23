package com.aifishing.guidance.runtime;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunRequest;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.persist.ShadowAgentRunWriter;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.guidance.spi.EvalRuntime;
import com.aifishing.guidance.spi.FishingAgentFacade;
import com.aifishing.guidance.spi.FishingAgentRuntime;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.guidance.versions.AgentPolicyResolver;
import com.aifishing.guidance.versions.ResolvedAgentPolicy;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Production entry. Callers must not pass authoritative {@code FishingSessionState}.
 * Persists RUNNING first, then resolves environment and builds one immutable state
 * snapshot for the whole run (including tool rounds).
 */
@Component
public class DefaultFishingAgentFacade implements FishingAgentFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultFishingAgentFacade.class);
    private static final DecisionValidationResult EMPTY_VALIDATION =
            new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of());

    private final DecisionPersistence persistence;
    private final EnvironmentSnapshotResolver environmentResolver;
    private final FishingSessionStateBuilder stateBuilder;
    private final FishingAgentRuntime runtime;
    private final ModelTurnClient modelTurnClient;
    private final GuidanceProperties properties;
    private final AgentPolicyResolver policyResolver;
    private final AgentRuntimeControlStore runtimeControlStore;
    private final EvalRuntime evalRuntime;
    private final ShadowAgentRunWriter shadowAgentRunWriter;
    private final GuidanceMetrics metrics;
    private final Clock clock;

    @Autowired
    public DefaultFishingAgentFacade(
            DecisionPersistence persistence,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            FishingAgentRuntime runtime,
            ModelTurnClient modelTurnClient,
            GuidanceProperties properties,
            AgentPolicyResolver policyResolver,
            AgentRuntimeControlStore runtimeControlStore,
            EvalRuntime evalRuntime,
            GuidanceMetrics metrics,
            Clock clock,
            ShadowAgentRunWriter shadowAgentRunWriter
    ) {
        this(
                persistence,
                environmentResolver,
                stateBuilder,
                runtime,
                modelTurnClient,
                properties,
                policyResolver,
                runtimeControlStore,
                evalRuntime,
                shadowAgentRunWriter,
                metrics,
                clock,
                true
        );
    }

    public DefaultFishingAgentFacade(
            DecisionPersistence persistence,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            FishingAgentRuntime runtime,
            ModelTurnClient modelTurnClient,
            GuidanceProperties properties,
            AgentPolicyResolver policyResolver,
            AgentRuntimeControlStore runtimeControlStore,
            EvalRuntime evalRuntime,
            GuidanceMetrics metrics,
            Clock clock
    ) {
        this(
                persistence,
                environmentResolver,
                stateBuilder,
                runtime,
                modelTurnClient,
                properties,
                policyResolver,
                runtimeControlStore,
                evalRuntime,
                null,
                metrics,
                clock,
                true
        );
    }

    public DefaultFishingAgentFacade(
            DecisionPersistence persistence,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            FishingAgentRuntime runtime,
            ModelTurnClient modelTurnClient,
            GuidanceProperties properties,
            AgentPolicyResolver policyResolver,
            AgentRuntimeControlStore runtimeControlStore,
            GuidanceMetrics metrics,
            Clock clock
    ) {
        this(
                persistence,
                environmentResolver,
                stateBuilder,
                runtime,
                modelTurnClient,
                properties,
                policyResolver,
                runtimeControlStore,
                null,
                null,
                metrics,
                clock,
                true
        );
    }

    private DefaultFishingAgentFacade(
            DecisionPersistence persistence,
            EnvironmentSnapshotResolver environmentResolver,
            FishingSessionStateBuilder stateBuilder,
            FishingAgentRuntime runtime,
            ModelTurnClient modelTurnClient,
            GuidanceProperties properties,
            AgentPolicyResolver policyResolver,
            AgentRuntimeControlStore runtimeControlStore,
            EvalRuntime evalRuntime,
            ShadowAgentRunWriter shadowAgentRunWriter,
            GuidanceMetrics metrics,
            Clock clock,
            boolean ignored
    ) {
        this.persistence = persistence;
        this.environmentResolver = environmentResolver;
        this.stateBuilder = stateBuilder;
        this.runtime = runtime;
        this.modelTurnClient = modelTurnClient;
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.runtimeControlStore = runtimeControlStore;
        this.evalRuntime = evalRuntime;
        this.shadowAgentRunWriter = shadowAgentRunWriter;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public AgentRunResult run(UUID sessionId, GuidanceTrigger trigger, OptionalUserInput optionalUserInput) {
        return run(sessionId, trigger, optionalUserInput, null);
    }

    @Override
    public AgentRunResult run(
            UUID sessionId,
            GuidanceTrigger trigger,
            OptionalUserInput optionalUserInput,
            UUID triggerOutboxId
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(trigger, "trigger");
        UUID runId = UUID.randomUUID();
        String traceId = AgentRunMdc.resolveTraceId();
        Instant started = clock.instant();
        Instant deadline = started.plusMillis(Math.max(1L, properties.getRunTimeoutMs()));
        AgentRuntimeControl control = runtimeControlStore.load();
        ResolvedAgentPolicy policy = bindProductionPolicy(control);
        AgentRunMetadata metadata = policy.toMetadata(null, runId);
        try (AgentRunMdc.Scope ignored = AgentRunMdc.open(runId, sessionId, trigger, traceId)) {
            metrics.record(GuidanceMetrics.PERSISTENCE, pendingTags(trigger), () -> persistence.createRunning(
                    runId, sessionId, trigger, traceId, metadata, triggerOutboxId
            ));
            if (!control.agentEnabled()) {
                return skipKillSwitch(runId, sessionId, trigger, policy, traceId, started);
            }
            try {
                EnvironmentSnapshot environment = metrics.record(
                        GuidanceMetrics.ENVIRONMENT,
                        pendingTags(trigger),
                        () -> environmentResolver.resolve(sessionId)
                );
                FishingSessionState state = metrics.record(
                        GuidanceMetrics.STATE,
                        pendingTags(trigger),
                        () -> stateBuilder.build(sessionId, environment)
                );
                persistence.appendStateSnapshot(runId, state);
                AgentRunSnapshot snapshot = new AgentRunSnapshot(
                        runId,
                        sessionId,
                        trigger,
                        environment,
                        state,
                        null,
                        optionalUserInput,
                        traceId,
                        deadline,
                        policy
                );
                AgentRunResult result = runtime.execute(snapshot);
                maybeShadow(snapshot, result, control);
                recordTotal(trigger, result, started);
                return result;
            } catch (RuntimeException ex) {
                log.warn("Guidance run failed before a decision: {}", ex.getMessage());
                DeliveredDecision delivered = GuidanceFallback.stay(
                        runId,
                        null,
                        GuidanceFallback.RUN_FAILED,
                        ex.getMessage()
                );
                try {
                    persistence.finalize(runId, AgentRunStatus.FAILED, delivered);
                } catch (RuntimeException persistError) {
                    log.warn("Failed to finalize crashed guidance run {}: {}", runId, persistError.getMessage());
                }
                AgentRunResult result = skippedResult(
                        runId, sessionId, trigger, policy, traceId, AgentRunStatus.FAILED, delivered
                );
                recordTotal(trigger, result, started);
                return result;
            }
        }
    }

    private AgentRunResult skipKillSwitch(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            ResolvedAgentPolicy policy,
            String traceId,
            Instant started
    ) {
        log.info("Guidance run skipped: {}", GuidanceFallback.KILL_SWITCH);
        FishingSessionState state = null;
        try {
            EnvironmentSnapshot environment = environmentResolver.resolve(sessionId);
            state = stateBuilder.build(sessionId, environment);
            persistence.appendStateSnapshot(runId, state);
        } catch (RuntimeException ex) {
            log.warn("Kill-switch skip could not snapshot state for session {}: {}", sessionId, ex.getMessage());
        }
        DeliveredDecision continuation = KillSwitchContinuation.from(runId, state);
        try {
            persistence.finalizeSkipped(runId, AgentRunStatus.FALLBACK, GuidanceFallback.KILL_SWITCH);
        } catch (RuntimeException persistError) {
            log.warn("Failed to finalize kill-switch skip {}: {}", runId, persistError.getMessage());
        }
        AgentRunResult result = skippedResult(
                runId, sessionId, trigger, policy, traceId, AgentRunStatus.FALLBACK, continuation
        );
        recordTotal(trigger, result, started);
        return result;
    }

    /**
     * Bind once from the live production version. Control PATCH does not rewrite
     * this run; only later runs pick up a new productionVersion.
     */
    private ResolvedAgentPolicy bindProductionPolicy(AgentRuntimeControl control) {
        return policyResolver.resolve(control.productionVersion());
    }

    private void maybeShadow(AgentRunSnapshot snapshot, AgentRunResult production, AgentRuntimeControl control) {
        if (evalRuntime == null || production == null || !control.liveShadowActive()) {
            return;
        }
        try {
            FrozenAgentRunSnapshot frozen = freeze(snapshot, production);
            AgentRunResult shadow = evalRuntime.execute(frozen, ReplayMode.FROZEN_REPLAY, control.candidateVersion());
            if (shadowAgentRunWriter != null && shadow != null) {
                shadowAgentRunWriter.persist(frozen, shadow, control.candidateVersion());
            }
        } catch (RuntimeException ex) {
            log.warn("Live shadow failed for run {}: {}", snapshot.runId(), ex.getMessage());
        }
    }

    static FrozenAgentRunSnapshot freeze(AgentRunSnapshot snapshot, AgentRunResult result) {
        FishingAgentContext context = result.request() == null ? snapshot.context() : result.request().context();
        RetrievedMemory memory = context == null
                ? RetrievedMemory.empty()
                : new RetrievedMemory(
                        context.userPreferences(),
                        context.inferredPreferences(),
                        context.retrievedMemoryIds()
                );
        Instant recorded = snapshot.environment() == null || snapshot.environment().resolvedAt() == null
                ? Instant.now()
                : snapshot.environment().resolvedAt();
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                snapshot.runId(),
                snapshot.sessionId(),
                snapshot.trigger(),
                snapshot.state(),
                context,
                memory,
                result.toolCalls() == null ? List.of() : result.toolCalls(),
                recorded,
                snapshot.policy() == null ? null : snapshot.policy().toEvalComponentVersions()
        );
    }

    private AgentRunResult skippedResult(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            ResolvedAgentPolicy policy,
            String traceId,
            AgentRunStatus status,
            DeliveredDecision delivered
    ) {
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                runId,
                status,
                new AgentRunRequest(
                        GuidanceSchemaVersion.VALUE,
                        runId,
                        sessionId,
                        trigger,
                        null,
                        null,
                        List.of(),
                        policy.model().provider(),
                        policy.model().modelName(),
                        policy.model().modelVersion(),
                        policy.prompt().version(),
                        policy.toolset().version(),
                        policy.context().version(),
                        null,
                        runId,
                        traceId
                ),
                List.of(),
                null,
                EMPTY_VALIDATION,
                delivered
        );
    }

    private void recordTotal(GuidanceTrigger trigger, AgentRunResult result, Instant started) {
        metrics.record(
                GuidanceMetrics.TOTAL,
                GuidanceMetrics.base(
                        trigger,
                        result.status(),
                        result.delivered() != null && result.delivered().fallbackUsed(),
                        modelTurnClient.modelName()
                ),
                Duration.between(started, clock.instant())
        );
    }

    private Tags pendingTags(GuidanceTrigger trigger) {
        return GuidanceMetrics.base(trigger, AgentRunStatus.RUNNING, false, modelTurnClient.modelName());
    }
}
