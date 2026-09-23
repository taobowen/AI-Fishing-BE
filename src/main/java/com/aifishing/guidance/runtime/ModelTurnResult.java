package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;

import java.util.List;

/**
 * Provider-neutral model-turn outcome. OpenAI types must not appear here.
 * Stub for the later runtime loop; do not assume every turn yields a candidate.
 */
public sealed interface ModelTurnResult {

    record ToolCalls(List<ToolRequestEnvelope> requests) implements ModelTurnResult {
    }

    record FinalCandidateDecision(CandidateDecision decision) implements ModelTurnResult {
    }

    record Refusal(String reason) implements ModelTurnResult {
    }

    record Incomplete(String reason) implements ModelTurnResult {
    }

    record Error(String errorType, String message) implements ModelTurnResult {
    }
}
