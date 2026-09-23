package com.aifishing.guidance.api.admin;

import java.time.Instant;

public record AgentRuntimeControlResponse(
        boolean agentEnabled,
        String productionVersion,
        String candidateVersion,
        boolean shadowEnabled,
        boolean learningEnabled,
        Instant updatedAt
) {
}
