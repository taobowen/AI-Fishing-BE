package com.aifishing.guidance.runtime;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunRequest;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.SafetyVerdictLevel;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.obs.GuidanceMetrics;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.guidance.spi.DecisionValidator;
import com.aifishing.guidance.spi.FishingAgentContextBuilder;
import com.aifishing.guidance.spi.FishingAgentRuntime;
import com.aifishing.guidance.spi.MemoryRetrievalService;
import com.aifishing.guidance.spi.SafetyRuleEngine;
import com.aifishing.guidance.tools.AgentToolExecutor;
import com.aifishing.guidance.tools.ToolExecutionBudget;
import com.aifishing.guidance.tools.ToolRoundResult;
import com.aifishing.guidance.versions.ResolvedAgentPolicy;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot replay / inner loop. Production callers enter through
 * {@link DefaultFishingAgentFacade}. Assumes {@code createRunning} already committed.
 */
@Component
public class DefaultFishingAgentRuntime implements FishingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DefaultFishingAgentRuntime.class);
    private static final DecisionValidationResult EMPTY_VALIDATION =
            new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of());

    private final SafetyRuleEngine safetyRuleEngine;
    private final MemoryRetrievalService memoryRetrievalService;
    private final FishingAgentContextBuilder contextBuilder;
    private final ModelTurnClient modelTurnClient;
    private final AgentToolExecutor toolExecutor;
    private final DecisionValidator decisionValidator;
    private final DecisionPersistence persistence;
    private final GuidanceProperties properties;
    private final GuidanceMetrics metrics;
    private final Clock clock;

    public DefaultFishingAgentRuntime(
            SafetyRuleEngine safetyRuleEngine,
            MemoryRetrievalService memoryRetrievalService,
            FishingAgentContextBuilder contextBuilder,
            ModelTurnClient modelTurnClient,
            AgentToolExecutor toolExecutor,
            DecisionValidator decisionValidator,
            DecisionPersistence persistence,
            GuidanceProperties properties,
            GuidanceMetrics metrics,
            Clock clock
    ) {
        this.safetyRuleEngine = safetyRuleEngine;
        this.memoryRetrievalService = memoryRetrievalService;
        this.contextBuilder = contextBuilder;
        this.modelTurnClient = modelTurnClient;
        this.toolExecutor = toolExecutor;
        this.decisionValidator = decisionValidator;
        this.persistence = persistence;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public AgentRunResult execute(AgentRunSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(snapshot.state(), "snapshot.state");
        Instant deadline = snapshot.runDeadline() == null
                ? clock.instant().plusMillis(Math.max(1L, properties.getRunTimeoutMs()))
                : snapshot.runDeadline();
        UUID decisionId = snapshot.runId();
        persistence.updateMetadata(snapshot.runId(), metadata(snapshot, decisionId));
        persistence.appendStateSnapshot(snapshot.runId(), snapshot.state());

        SafetyVerdict safety = metrics.record(
                GuidanceMetrics.SAFETY,
                pendingTags(snapshot.trigger()),
                () -> safetyRuleEngine.evaluate(snapshot.state())
        );
        if (safety.level() == SafetyVerdictLevel.BLOCK) {
            DeliveredDecision delivered = GuidanceFallback.prescribed(
                    decisionId,
                    safety.prescribedAction(),
                    snapshot.state(),
                    safety.reasons().isEmpty() ? null : safety.reasons().getFirst()
            );
            return finish(
                    snapshot,
                    null,
                    List.of(),
                    null,
                    EMPTY_VALIDATION,
                    delivered,
                    AgentRunStatus.FALLBACK
            );
        }

        if (expired(deadline)) {
            return timeout(snapshot, decisionId, List.of(), null, EMPTY_VALIDATION);
        }

        FishingAgentContext context = snapshot.context();
        if (context == null) {
            context = metrics.record(
                    GuidanceMetrics.CONTEXT,
                    pendingTags(snapshot.trigger()),
                    () -> {
                        RetrievedMemory memory = memoryRetrievalService.retrieve(
                                snapshot.sessionId(), snapshot.state(), snapshot.trigger());
                        return contextBuilder(snapshot).build(snapshot.state(), snapshot.trigger(), memory);
                    }
            );
            persistence.appendContextSnapshot(snapshot.runId(), context);
        }
        AgentRunSnapshot withContext = snapshot.withContext(context);

        if (expired(deadline)) {
            return timeout(withContext, decisionId, List.of(), null, EMPTY_VALIDATION);
        }

        ToolExecutionBudget budget = ToolExecutionBudget.from(properties)
                .runDeadline(deadline)
                .build();
        AgentToolExecutor tools = toolExecutor(snapshot);
        ModelTurnClient model = modelTurnClient(snapshot);
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        CandidateDecision candidate = null;
        DecisionValidationResult validation = EMPTY_VALIDATION;

        int maxTurns = Math.max(1, properties.getMaxModelTurns());
        for (int turn = 0; turn < maxTurns; turn++) {
            if (expired(deadline)) {
                return timeout(withContext, decisionId, toolCalls, candidate, validation);
            }
            ModelTurnInput input = new ModelTurnInput(
                    withContext.runId(),
                    decisionId,
                    withContext.trigger(),
                    withContext.state(),
                    withContext.context(),
                    withContext.userInput(),
                    toolCalls,
                    turn,
                    deadline,
                    promptInstructions(withContext)
            );
            ModelTurnResult turnResult = metrics.record(
                    GuidanceMetrics.MODEL,
                    pendingTags(withContext.trigger()),
                    () -> model.nextTurn(input)
            );
            if (expired(deadline)) {
                if (turnResult instanceof ModelTurnResult.FinalCandidateDecision(var decision)) {
                    candidate = decision;
                    persistence.appendCandidate(withContext.runId(), candidate);
                }
                return timeout(withContext, decisionId, toolCalls, candidate, validation);
            }
            switch (turnResult) {
                case ModelTurnResult.ToolCalls(var requests) -> {
                    if (expired(deadline)) {
                        return timeout(withContext, decisionId, toolCalls, candidate, validation);
                    }
                    if (!budget.hasRoundRemaining() || !budget.hasCallRemaining()) {
                        DeliveredDecision delivered = GuidanceFallback.stay(
                                decisionId,
                                withContext.state(),
                                GuidanceFallback.TOOL_BUDGET,
                                "Tool budget exhausted before observations could be gathered."
                        );
                        return finish(
                                withContext,
                                withContext.context(),
                                toolCalls,
                                candidate,
                                validation,
                                delivered,
                                AgentRunStatus.FALLBACK
                        );
                    }
                    ToolRoundResult round = metrics.record(
                            GuidanceMetrics.TOOLS,
                            pendingTags(withContext.trigger()),
                            () -> tools.executeRound(requests, budget)
                    );
                    if (round.executed()) {
                        for (ToolCallRecord record : round.records()) {
                            persistence.appendToolCall(withContext.runId(), record);
                            toolCalls.add(record);
                            Tags toolTags = GuidanceMetrics.withTool(
                                    pendingTags(withContext.trigger()),
                                    record.request() == null ? null : record.request().toolName()
                            );
                            metrics.record(
                                    GuidanceMetrics.TOOLS,
                                    toolTags,
                                    Duration.ofMillis(record.latencyMs() == null ? 0 : record.latencyMs())
                            );
                        }
                    } else {
                        DeliveredDecision delivered = GuidanceFallback.stay(
                                decisionId,
                                withContext.state(),
                                GuidanceFallback.TOOL_BUDGET,
                                "Tool round skipped: " + round.skipReason()
                        );
                        return finish(
                                withContext,
                                withContext.context(),
                                toolCalls,
                                candidate,
                                validation,
                                delivered,
                                AgentRunStatus.FALLBACK
                        );
                    }
                }
                case ModelTurnResult.FinalCandidateDecision(var decision) -> {
                    candidate = decision;
                    persistence.appendCandidate(withContext.runId(), candidate);
                    CandidateDecision toValidate = candidate;
                    validation = metrics.record(
                            GuidanceMetrics.VALIDATION,
                            pendingTags(withContext.trigger()),
                            () -> decisionValidator.validate(toValidate, withContext.state(), safety)
                    );
                    persistence.appendValidation(withContext.runId(), validation);
                    if (!validation.issues().isEmpty()) {
                        metrics.record(
                                GuidanceMetrics.VALIDATION,
                                GuidanceMetrics.withValidation(pendingTags(withContext.trigger()), validation.issues().getFirst().check()),
                                Duration.ZERO
                        );
                    }
                    if (validation.valid()) {
                        return finish(
                                withContext,
                                withContext.context(),
                                toolCalls,
                                candidate,
                                validation,
                                GuidanceFallback.fromCandidate(candidate),
                                AgentRunStatus.COMPLETED
                        );
                    }
                    DeliveredDecision delivered = GuidanceFallback.stay(
                            decisionId,
                            withContext.state(),
                            GuidanceFallback.VALIDATION_FAILED,
                            validation.issues().isEmpty()
                                    ? "Candidate failed validation."
                                    : validation.issues().getFirst().message()
                    );
                    return finish(
                            withContext,
                            withContext.context(),
                            toolCalls,
                            candidate,
                            validation,
                            delivered,
                            AgentRunStatus.FALLBACK
                    );
                }
                case ModelTurnResult.Refusal(var reason) -> {
                    DeliveredDecision delivered = GuidanceFallback.stay(
                            decisionId,
                            withContext.state(),
                            GuidanceFallback.MODEL_REFUSAL,
                            reason
                    );
                    return finish(
                            withContext,
                            withContext.context(),
                            toolCalls,
                            candidate,
                            validation,
                            delivered,
                            AgentRunStatus.FALLBACK
                    );
                }
                case ModelTurnResult.Incomplete(var reason) -> {
                    DeliveredDecision delivered = GuidanceFallback.stay(
                            decisionId,
                            withContext.state(),
                            GuidanceFallback.MODEL_INCOMPLETE,
                            reason
                    );
                    return finish(
                            withContext,
                            withContext.context(),
                            toolCalls,
                            candidate,
                            validation,
                            delivered,
                            AgentRunStatus.FALLBACK
                    );
                }
                case ModelTurnResult.Error(var errorType, var message) -> {
                    log.warn("Model turn error type={} message={}", errorType, message);
                    DeliveredDecision delivered = GuidanceFallback.stay(
                            decisionId,
                            withContext.state(),
                            GuidanceFallback.MODEL_ERROR,
                            GuidanceFallback.MODEL_ERROR_EXPLANATION
                    );
                    return finish(
                            withContext,
                            withContext.context(),
                            toolCalls,
                            candidate,
                            validation,
                            delivered,
                            AgentRunStatus.FALLBACK
                    );
                }
            }
        }

        DeliveredDecision delivered = GuidanceFallback.stay(
                decisionId,
                withContext.state(),
                GuidanceFallback.MAX_MODEL_TURNS,
                "Model turn budget exhausted without a final candidate."
        );
        return finish(
                withContext,
                withContext.context(),
                toolCalls,
                candidate,
                validation,
                delivered,
                AgentRunStatus.FALLBACK
        );
    }

    AgentRunResult timeout(
            AgentRunSnapshot snapshot,
            UUID decisionId,
            List<ToolCallRecord> toolCalls,
            CandidateDecision candidate,
            DecisionValidationResult validation
    ) {
        DeliveredDecision delivered = GuidanceFallback.stay(
                decisionId,
                snapshot.state(),
                GuidanceFallback.RUN_TIMEOUT,
                "Run wall-clock budget exhausted."
        );
        return finish(
                snapshot,
                snapshot.context(),
                toolCalls,
                candidate,
                validation == null ? EMPTY_VALIDATION : validation,
                delivered,
                AgentRunStatus.TIMEOUT
        );
    }

    AgentRunResult finish(
            AgentRunSnapshot snapshot,
            FishingAgentContext context,
            List<ToolCallRecord> toolCalls,
            CandidateDecision candidate,
            DecisionValidationResult validation,
            DeliveredDecision delivered,
            AgentRunStatus status
    ) {
        metrics.record(
                GuidanceMetrics.PERSISTENCE,
                tags(snapshot.trigger(), status, delivered.fallbackUsed(), modelName(snapshot)),
                () -> persistence.finalize(snapshot.runId(), status, delivered)
        );
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                snapshot.runId(),
                status,
                request(snapshot, context, delivered.decisionId()),
                List.copyOf(toolCalls),
                candidate,
                validation,
                delivered
        );
    }

    private AgentRunRequest request(AgentRunSnapshot snapshot, FishingAgentContext context, UUID decisionId) {
        FishingAgentContext echo = context;
        if (echo == null) {
            echo = contextBuilder(snapshot).build(snapshot.state(), snapshot.trigger(), RetrievedMemory.empty());
        }
        ModelTurnClient model = modelTurnClient(snapshot);
        ResolvedAgentPolicy policy = snapshot.policy();
        return new AgentRunRequest(
                GuidanceSchemaVersion.VALUE,
                snapshot.runId(),
                snapshot.sessionId(),
                snapshot.trigger(),
                snapshot.state(),
                echo,
                echo == null || echo.retrievedMemoryIds() == null ? List.of() : echo.retrievedMemoryIds(),
                policy != null ? policy.model().provider() : model.provider(),
                policy != null ? policy.model().modelName() : model.modelName(),
                policy != null ? policy.model().modelVersion() : model.modelVersion(),
                policy != null ? policy.prompt().version() : null,
                policy != null ? policy.toolset().version() : null,
                policy != null ? policy.context().version() : null,
                snapshot.state().plan() == null ? null : snapshot.state().plan().currentGuidancePlanVersion(),
                decisionId,
                snapshot.traceId()
        );
    }

    private AgentRunMetadata metadata(AgentRunSnapshot snapshot, UUID decisionId) {
        if (snapshot.policy() != null) {
            return snapshot.policy().toMetadata(null, decisionId);
        }
        ModelTurnClient model = modelTurnClient(snapshot);
        return new AgentRunMetadata(
                model.provider(),
                model.modelName(),
                model.modelVersion(),
                null,
                null,
                null,
                null,
                decisionId
        );
    }

    private FishingAgentContextBuilder contextBuilder(AgentRunSnapshot snapshot) {
        if (snapshot.policy() != null && snapshot.policy().context() != null
                && snapshot.policy().context().builder() != null) {
            return snapshot.policy().context().builder();
        }
        return contextBuilder;
    }

    private ModelTurnClient modelTurnClient(AgentRunSnapshot snapshot) {
        if (snapshot.policy() != null && snapshot.policy().model() != null
                && snapshot.policy().model().client() != null) {
            return snapshot.policy().model().client();
        }
        return modelTurnClient;
    }

    private AgentToolExecutor toolExecutor(AgentRunSnapshot snapshot) {
        if (snapshot.policy() != null && snapshot.policy().toolset() != null
                && snapshot.policy().toolset().registry() != null) {
            return new AgentToolExecutor(snapshot.policy().toolset().registry(), properties, clock);
        }
        return toolExecutor;
    }

    private static String promptInstructions(AgentRunSnapshot snapshot) {
        if (snapshot.policy() == null || snapshot.policy().prompt() == null) {
            return null;
        }
        return snapshot.policy().prompt().instructions();
    }

    private String modelName(AgentRunSnapshot snapshot) {
        return modelTurnClient(snapshot).modelName();
    }

    private boolean expired(Instant deadline) {
        return !clock.instant().isBefore(deadline);
    }

    private Tags pendingTags(GuidanceTrigger trigger) {
        return GuidanceMetrics.base(trigger, AgentRunStatus.RUNNING, false, modelTurnClient.modelName());
    }

    private static Tags tags(GuidanceTrigger trigger, AgentRunStatus status, boolean fallback, String model) {
        return GuidanceMetrics.base(trigger, status, fallback, model);
    }
}
