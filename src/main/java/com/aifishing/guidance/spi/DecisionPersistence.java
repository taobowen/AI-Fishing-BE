package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.runtime.AgentRunMetadata;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit lifecycle: insert RUNNING, append stage payloads, then finalize.
 * There is no single terminal {@code save(AgentRunResult)}.
 */
public interface DecisionPersistence {

    void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata
    );

    /**
     * Same as {@link #createRunning(UUID, UUID, GuidanceTrigger, String, AgentRunMetadata)}
     * with optional {@code triggerOutboxId} for exact Trigger→AgentRun correlation.
     * {@code USER_REQUEST} passes {@code null}.
     */
    default void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata,
            UUID triggerOutboxId
    ) {
        createRunning(runId, sessionId, trigger, traceId, metadata);
    }

    void appendStateSnapshot(UUID runId, FishingSessionState state);

    void appendContextSnapshot(UUID runId, FishingAgentContext context);

    void appendToolCall(UUID runId, ToolCallRecord toolCall);

    void appendCandidate(UUID runId, CandidateDecision candidate);

    void appendValidation(UUID runId, DecisionValidationResult validation);

    void updateMetadata(UUID runId, AgentRunMetadata metadata);

    void finalize(UUID runId, AgentRunStatus status, DeliveredDecision delivered);

    /**
     * Terminalize a run without an {@code agent_delivered_decisions} row so
     * {@link #current(UUID)} keeps the last real Agent decision.
     */
    default void finalizeSkipped(UUID runId, AgentRunStatus status, String fallbackReason) {
        finalize(runId, status, null);
    }

    Optional<DeliveredDecision> current(UUID sessionId);
}
