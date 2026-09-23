package com.aifishing.guidance.runtime;

import java.util.UUID;

public record AgentRunMetadata(
        String modelProvider,
        String modelName,
        String modelVersion,
        String promptVersion,
        String toolSchemaVersion,
        String contextVersion,
        Integer guidancePlanVersion,
        UUID decisionId,
        String agentPolicyVersion,
        String learningAlgorithmVersion,
        String learningSnapshotVersion
) {
    public AgentRunMetadata(
            String modelProvider,
            String modelName,
            String modelVersion,
            String promptVersion,
            String toolSchemaVersion,
            String contextVersion,
            Integer guidancePlanVersion,
            UUID decisionId
    ) {
        this(
                modelProvider,
                modelName,
                modelVersion,
                promptVersion,
                toolSchemaVersion,
                contextVersion,
                guidancePlanVersion,
                decisionId,
                null,
                null,
                null
        );
    }
}
