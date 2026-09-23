package com.aifishing.guidance.control;

import com.aifishing.guidance.versions.AgentPolicyVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeControlTest {

    private static final Instant NOW = Instant.parse("2026-09-18T14:00:00Z");

    @Test
    void liveShadowRequiresAgentShadowAndCandidate() {
        assertThat(new AgentRuntimeControl(true, AgentPolicyVersion.V1, AgentPolicyVersion.V2, true, true, NOW)
                .liveShadowActive()).isTrue();
        assertThat(new AgentRuntimeControl(false, AgentPolicyVersion.V1, AgentPolicyVersion.V2, true, true, NOW)
                .liveShadowActive()).isFalse();
        assertThat(new AgentRuntimeControl(true, AgentPolicyVersion.V1, AgentPolicyVersion.V2, false, true, NOW)
                .liveShadowActive()).isFalse();
        assertThat(new AgentRuntimeControl(true, AgentPolicyVersion.V1, null, true, true, NOW)
                .liveShadowActive()).isFalse();
        assertThat(new AgentRuntimeControl(true, AgentPolicyVersion.V1, "  ", true, true, NOW)
                .liveShadowActive()).isFalse();
    }
}
