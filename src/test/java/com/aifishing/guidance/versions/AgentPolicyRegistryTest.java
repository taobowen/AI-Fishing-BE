package com.aifishing.guidance.versions;

import com.aifishing.guidance.GuidanceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPolicyRegistryTest {

    @Test
    void catalogsV1AndV2AsAvailableClones() {
        AgentPolicyRegistry registry = new AgentPolicyRegistry(new GuidanceProperties());

        AgentPolicyVersion v1 = registry.require(AgentPolicyVersion.V1);
        AgentPolicyVersion v2 = registry.require(AgentPolicyVersion.V2);

        assertThat(registry.defaultVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(registry.all()).extracting(AgentPolicyVersion::version)
                .containsExactly(AgentPolicyVersion.V1, AgentPolicyVersion.V2);
        assertThat(v1.promptProfile()).isEqualTo(PromptProfile.GUIDANCE_PROMPT_V1);
        assertThat(v1.contextProfile()).isEqualTo(ContextProfile.GUIDANCE_CONTEXT_V1);
        assertThat(v1.toolsetProfile()).isEqualTo(ToolsetProfile.GUIDANCE_TOOLS_V1);
        assertThat(v1.modelProfile()).isEqualTo(ModelProfile.CURRENT);
        assertThat(v1.learningProfile()).isEqualTo(LearningProfile.EMPIRICAL_ALGORITHM_1);
        assertThat(v2.promptProfile()).isEqualTo(v1.promptProfile());
        assertThat(v2.contextProfile()).isEqualTo(v1.contextProfile());
        assertThat(v2.toolsetProfile()).isEqualTo(v1.toolsetProfile());
        assertThat(v2.modelProfile()).isEqualTo(v1.modelProfile());
        assertThat(v2.learningProfile()).isEqualTo(v1.learningProfile());
    }

    @Test
    void requireRejectsUnknownVersion() {
        AgentPolicyRegistry registry = new AgentPolicyRegistry(new GuidanceProperties());

        assertThatThrownBy(() -> registry.require("v9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent policy version");
        assertThatThrownBy(() -> registry.require(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }
}
