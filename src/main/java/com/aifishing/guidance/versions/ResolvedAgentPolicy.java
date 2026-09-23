package com.aifishing.guidance.versions;

import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.runtime.AgentRunMetadata;

import java.util.UUID;

/**
 * Bound runtime policy. Callers use the nested profiles; they must not branch on
 * {@code version} strings.
 */
public record ResolvedAgentPolicy(
        String version,
        PromptProfile prompt,
        ContextProfile context,
        ToolsetProfile toolset,
        ModelProfile model,
        LearningProfile learning
) {
    public ResolvedAgentPolicy withToolset(ToolsetProfile toolset) {
        return new ResolvedAgentPolicy(version, prompt, context, toolset, model, learning);
    }

    public AgentRunMetadata toMetadata(Integer guidancePlanVersion, UUID decisionId) {
        return new AgentRunMetadata(
                model.provider(),
                model.modelName(),
                model.modelVersion(),
                prompt.version(),
                toolset.version(),
                context.version(),
                guidancePlanVersion,
                decisionId,
                version,
                learning.learningAlgorithmVersion(),
                learning.learningSnapshotVersion()
        );
    }

    public EvalComponentVersions toEvalComponentVersions() {
        return new EvalComponentVersions(
                prompt.version(),
                model.provider(),
                model.modelName(),
                model.modelVersion(),
                toolset.version(),
                context.version(),
                null,
                null,
                null,
                version,
                learning.learningAlgorithmVersion(),
                learning.learningSnapshotVersion()
        );
    }
}
