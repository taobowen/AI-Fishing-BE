package com.aifishing.guidance.versions;

import com.aifishing.guidance.runtime.ModelTurnClient;
import com.aifishing.guidance.spi.AgentToolRegistry;
import com.aifishing.guidance.spi.FishingAgentContextBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Maps a catalog policy version onto prompt/context/toolset/model/learning
 * profiles. Runtime binds one {@link ResolvedAgentPolicy} at run start.
 */
@Component
public class AgentPolicyResolver {

    private final AgentPolicyRegistry registry;
    private final Map<String, PromptProfile> prompts;
    private final Map<String, ContextProfile> contexts;
    private final Map<String, ToolsetProfile> toolsets;
    private final Map<String, ModelProfile> models;
    private final Map<String, LearningProfile> learning;

    public AgentPolicyResolver(
            AgentPolicyRegistry registry,
            FishingAgentContextBuilder contextBuilder,
            AgentToolRegistry agentToolRegistry,
            ModelTurnClient modelTurnClient
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.prompts = Map.of(PromptProfile.GUIDANCE_PROMPT_V1, PromptProfile.guidanceV1());
        this.contexts = Map.of(
                ContextProfile.GUIDANCE_CONTEXT_V1,
                new ContextProfile(ContextProfile.GUIDANCE_CONTEXT_V1, Objects.requireNonNull(contextBuilder, "contextBuilder"))
        );
        this.toolsets = Map.of(
                ToolsetProfile.GUIDANCE_TOOLS_V1,
                new ToolsetProfile(ToolsetProfile.GUIDANCE_TOOLS_V1, Objects.requireNonNull(agentToolRegistry, "agentToolRegistry"))
        );
        this.models = Map.of(
                ModelProfile.CURRENT,
                ModelProfile.current(Objects.requireNonNull(modelTurnClient, "modelTurnClient"))
        );
        this.learning = Map.of(LearningProfile.EMPIRICAL_ALGORITHM_1, LearningProfile.empiricalAlgorithm1());
        for (AgentPolicyVersion version : registry.all()) {
            resolve(version);
        }
    }

    public ResolvedAgentPolicy resolve(String version) {
        return resolve(registry.require(version));
    }

    public ResolvedAgentPolicy resolve(AgentPolicyVersion catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return new ResolvedAgentPolicy(
                catalog.version(),
                require(prompts, catalog.promptProfile(), "prompt"),
                require(contexts, catalog.contextProfile(), "context"),
                require(toolsets, catalog.toolsetProfile(), "toolset"),
                require(models, catalog.modelProfile(), "model"),
                require(learning, catalog.learningProfile(), "learning")
        );
    }

    private static <T> T require(Map<String, T> catalog, String id, String kind) {
        T found = catalog.get(id);
        if (found == null) {
            throw new IllegalArgumentException("Unknown " + kind + " profile: " + id);
        }
        return found;
    }
}
