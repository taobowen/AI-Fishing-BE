package com.aifishing.guidance.contracts;

import java.util.List;
import java.util.UUID;

public record AgentRunRequest(
        String schemaVersion,
        UUID runId,
        UUID sessionId,
        GuidanceTrigger trigger,
        FishingSessionState state,
        FishingAgentContext context,
        List<String> memoryRefIds,
        String modelProvider,
        String modelName,
        String modelVersion,
        String promptVersion,
        String toolSchemaVersion,
        String contextVersion,
        Integer guidancePlanVersion,
        UUID decisionId,
        String traceId
) {
}
