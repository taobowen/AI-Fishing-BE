package com.aifishing.guidance.contracts;

public record EvalComponentVersions(
        String promptVersion,
        String modelProvider,
        String modelName,
        String modelVersion,
        String toolSchemaVersion,
        String contextVersion,
        String memoryRetrievalVersion,
        String safetyVersion,
        String validatorVersion,
        String agentPolicyVersion,
        String learningAlgorithmVersion,
        String learningSnapshotVersion
) {
    public EvalComponentVersions(
            String promptVersion,
            String modelProvider,
            String modelName,
            String modelVersion,
            String toolSchemaVersion,
            String contextVersion,
            String memoryRetrievalVersion,
            String safetyVersion,
            String validatorVersion
    ) {
        this(
                promptVersion,
                modelProvider,
                modelName,
                modelVersion,
                toolSchemaVersion,
                contextVersion,
                memoryRetrievalVersion,
                safetyVersion,
                validatorVersion,
                null,
                null,
                null
        );
    }

    public static EvalComponentVersions empty() {
        return new EvalComponentVersions(
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }
}
