package com.aifishing.guidance.contracts;

import java.util.List;
import java.util.UUID;

public record AgentRunResult(
        String schemaVersion,
        UUID runId,
        AgentRunStatus status,
        AgentRunRequest request,
        List<ToolCallRecord> toolCalls,
        CandidateDecision candidate,
        DecisionValidationResult validation,
        DeliveredDecision delivered
) {
}
