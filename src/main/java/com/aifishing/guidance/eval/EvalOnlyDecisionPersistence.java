package com.aifishing.guidance.eval;

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

/**
 * In-memory audit for EVAL only. Never writes session delivered decisions,
 * trigger outbox, memory, or empirical tables.
 */
final class EvalOnlyDecisionPersistence implements DecisionPersistence {

    record Row(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            AgentRunStatus status,
            FishingSessionState state,
            FishingAgentContext context,
            DeliveredDecision delivered,
            List<ToolCallRecord> toolCalls
    ) {
    }

    private final ConcurrentHashMap<UUID, Row> rows = new ConcurrentHashMap<>();

    @Override
    public void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata
    ) {
        rows.put(runId, new Row(
                runId, sessionId, trigger, AgentRunStatus.RUNNING,
                null, null, null, new CopyOnWriteArrayList<>()
        ));
    }

    @Override
    public void appendStateSnapshot(UUID runId, FishingSessionState state) {
        rows.computeIfPresent(runId, (id, row) -> new Row(
                row.runId, row.sessionId, row.trigger, row.status,
                state, row.context, row.delivered, row.toolCalls
        ));
    }

    @Override
    public void appendContextSnapshot(UUID runId, FishingAgentContext context) {
        rows.computeIfPresent(runId, (id, row) -> new Row(
                row.runId, row.sessionId, row.trigger, row.status,
                row.state, context, row.delivered, row.toolCalls
        ));
    }

    @Override
    public void appendToolCall(UUID runId, ToolCallRecord toolCall) {
        Row row = rows.get(runId);
        if (row != null) {
            row.toolCalls.add(toolCall);
        }
    }

    @Override
    public void appendCandidate(UUID runId, CandidateDecision candidate) {
        // Candidate stays off session tables in EVAL.
    }

    @Override
    public void appendValidation(UUID runId, DecisionValidationResult validation) {
        // Validation stays off session tables in EVAL.
    }

    @Override
    public void updateMetadata(UUID runId, AgentRunMetadata metadata) {
        // Metadata stays off session tables in EVAL.
    }

    @Override
    public void finalize(UUID runId, AgentRunStatus status, DeliveredDecision delivered) {
        rows.computeIfPresent(runId, (id, row) -> new Row(
                row.runId, row.sessionId, row.trigger, status,
                row.state, row.context, delivered, row.toolCalls
        ));
    }

    @Override
    public Optional<DeliveredDecision> current(UUID sessionId) {
        return Optional.empty();
    }

    Optional<Row> get(UUID runId) {
        return Optional.ofNullable(rows.get(runId));
    }

    List<Row> all() {
        return new ArrayList<>(rows.values());
    }
}
