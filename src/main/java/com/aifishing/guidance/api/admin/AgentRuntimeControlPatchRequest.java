package com.aifishing.guidance.api.admin;

public record AgentRuntimeControlPatchRequest(
        Boolean agentEnabled,
        String productionVersion,
        String candidateVersion,
        Boolean shadowEnabled,
        Boolean learningEnabled
) {
}
