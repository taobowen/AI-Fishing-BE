package com.aifishing.guidance.versions;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.empirical.EmpiricalAlgorithm;
import com.aifishing.guidance.runtime.DeterministicModelTurnClient;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.aifishing.guidance.tools.InMemoryAgentToolRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPolicyResolverTest {

    @Test
    void v1AndV2ResolveToIdenticalCurrentProductionProfiles() {
        AgentPolicyResolver resolver = resolver();

        ResolvedAgentPolicy v1 = resolver.resolve(AgentPolicyVersion.V1);
        ResolvedAgentPolicy v2 = resolver.resolve(AgentPolicyVersion.V2);

        assertThat(v1.prompt().instructions()).isEqualTo(PromptProfile.guidanceV1().instructions());
        assertThat(v1.prompt().version()).isEqualTo("guidance-prompt-v1");
        assertThat(v1.context().version()).isEqualTo("guidance-context-v1");
        assertThat(v1.toolset().version()).isEqualTo("guidance-tools-v1");
        assertThat(v1.model().version()).isEqualTo(ModelProfile.CURRENT);
        assertThat(v1.learning().learningAlgorithmVersion())
                .isEqualTo(String.valueOf(EmpiricalAlgorithm.VERSION));
        assertThat(v1.learning().learningSnapshotVersion()).isNull();

        assertThat(v2.prompt()).isEqualTo(v1.prompt());
        assertThat(v2.context().version()).isEqualTo(v1.context().version());
        assertThat(v2.toolset().version()).isEqualTo(v1.toolset().version());
        assertThat(v2.model().provider()).isEqualTo(v1.model().provider());
        assertThat(v2.learning()).isEqualTo(v1.learning());
        assertThat(v1.toEvalComponentVersions().agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(v2.toEvalComponentVersions().agentPolicyVersion()).isEqualTo(AgentPolicyVersion.V2);
        assertThat(v2.toEvalComponentVersions().learningSnapshotVersion()).isNull();
    }

    @Test
    void resolveRejectsUnknownCatalogVersion() {
        assertThatThrownBy(() -> resolver().resolve("v9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    private static AgentPolicyResolver resolver() {
        GuidanceProperties properties = new GuidanceProperties();
        return new AgentPolicyResolver(
                new AgentPolicyRegistry(properties),
                new TriggerClippedFishingAgentContextBuilder(),
                InMemoryAgentToolRegistry.empty(),
                new DeterministicModelTurnClient()
        );
    }
}
