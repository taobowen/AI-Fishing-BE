package com.aifishing.guidance.control;

/**
 * Partial update. Null fields keep the current value. Blank
 * {@code candidateVersion} clears the candidate.
 */
public record AgentRuntimeControlPatch(
        Boolean agentEnabled,
        String productionVersion,
        String candidateVersion,
        Boolean shadowEnabled,
        Boolean learningEnabled
) {
}
