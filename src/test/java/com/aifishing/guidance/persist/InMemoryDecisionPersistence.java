package com.aifishing.guidance.persist;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.runtime.AgentRunMetadata;
import com.aifishing.guidance.spi.DecisionPersistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryDecisionPersistence implements DecisionPersistence {

    public record RunRow(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunStatus status,
            FishingSessionState state,
            FishingAgentContext context,
            CandidateDecision candidate,
            DecisionValidationResult validation,
            DeliveredDecision delivered,
            AgentRunMetadata metadata,
            List<ToolCallRecord> toolCalls,
            String fallbackReason
    ) {
    }

    private final ConcurrentHashMap<UUID, RunRow> runs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> order = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AgentRunStatus> statusLog = new CopyOnWriteArrayList<>();

    @Override
    public void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata
    ) {
        RunRow row = new RunRow(
                runId, sessionId, trigger, traceId, AgentRunStatus.RUNNING,
                null, null, null, null, null, metadata, new CopyOnWriteArrayList<>(), null
        );
        runs.put(runId, row);
        order.add(runId);
        statusLog.add(AgentRunStatus.RUNNING);
    }

    @Override
    public void appendStateSnapshot(UUID runId, FishingSessionState state) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, row.status,
                state, row.context, row.candidate, row.validation, row.delivered, row.metadata, row.toolCalls,
                row.fallbackReason
        ));
    }

    @Override
    public void appendContextSnapshot(UUID runId, FishingAgentContext context) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, row.status,
                row.state, context, row.candidate, row.validation, row.delivered, row.metadata, row.toolCalls,
                row.fallbackReason
        ));
    }

    @Override
    public void appendToolCall(UUID runId, ToolCallRecord toolCall) {
        RunRow row = require(runId);
        row.toolCalls.add(toolCall);
    }

    @Override
    public void appendCandidate(UUID runId, CandidateDecision candidate) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, row.status,
                row.state, row.context, candidate, row.validation, row.delivered, row.metadata, row.toolCalls,
                row.fallbackReason
        ));
    }

    @Override
    public void appendValidation(UUID runId, DecisionValidationResult validation) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, row.status,
                row.state, row.context, row.candidate, validation, row.delivered, row.metadata, row.toolCalls,
                row.fallbackReason
        ));
    }

    @Override
    public void updateMetadata(UUID runId, AgentRunMetadata metadata) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, row.status,
                row.state, row.context, row.candidate, row.validation, row.delivered,
                preserveVersions(row.metadata, metadata), row.toolCalls, row.fallbackReason
        ));
    }

    @Override
    public void finalize(UUID runId, AgentRunStatus status, DeliveredDecision delivered) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, status,
                row.state, row.context, row.candidate, row.validation, delivered, row.metadata, row.toolCalls,
                delivered == null ? row.fallbackReason : delivered.fallbackReason()
        ));
        statusLog.add(status);
    }

    @Override
    public void finalizeSkipped(UUID runId, AgentRunStatus status, String fallbackReason) {
        runs.computeIfPresent(runId, (id, row) -> new RunRow(
                row.runId, row.sessionId, row.trigger, row.traceId, status,
                row.state, row.context, row.candidate, row.validation, null, row.metadata, row.toolCalls,
                fallbackReason
        ));
        statusLog.add(status);
    }

    @Override
    public Optional<DeliveredDecision> current(UUID sessionId) {
        for (int i = order.size() - 1; i >= 0; i--) {
            RunRow row = runs.get(order.get(i));
            if (row != null && row.sessionId.equals(sessionId) && row.delivered != null) {
                return Optional.of(row.delivered);
            }
        }
        return Optional.empty();
    }

    public Optional<RunRow> get(UUID runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public Optional<RunRow> latest() {
        if (order.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(runs.get(order.get(order.size() - 1)));
    }

    public AgentRunStatus status(UUID runId) {
        return require(runId).status;
    }

    public List<AgentRunStatus> statusLog() {
        return new ArrayList<>(statusLog);
    }

    public boolean hasContext(UUID runId) {
        return require(runId).context != null;
    }

    private RunRow require(UUID runId) {
        RunRow row = runs.get(runId);
        if (row == null) {
            throw new IllegalStateException("agent run not found: " + runId);
        }
        return row;
    }

    /**
     * {@code updateMetadata} may change decision/plan ids but must not rewrite
     * policy or component version fields stamped at {@code createRunning}.
     */
    static AgentRunMetadata preserveVersions(AgentRunMetadata existing, AgentRunMetadata update) {
        if (update == null) {
            return existing;
        }
        if (existing == null) {
            return update;
        }
        return new AgentRunMetadata(
                existing.modelProvider(),
                existing.modelName(),
                existing.modelVersion(),
                existing.promptVersion(),
                existing.toolSchemaVersion(),
                existing.contextVersion(),
                update.guidancePlanVersion() != null ? update.guidancePlanVersion() : existing.guidancePlanVersion(),
                update.decisionId() != null ? update.decisionId() : existing.decisionId(),
                existing.agentPolicyVersion(),
                existing.learningAlgorithmVersion(),
                existing.learningSnapshotVersion()
        );
    }
}
