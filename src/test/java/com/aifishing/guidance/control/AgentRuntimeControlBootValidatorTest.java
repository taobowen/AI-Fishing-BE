package com.aifishing.guidance.control;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeControlBootValidatorTest {

    @Test
    void unknownProductionVersionFailsStartup() {
        AgentRuntimeControlStore store = mock(AgentRuntimeControlStore.class);
        when(store.require()).thenReturn(new AgentRuntimeControl(
                true, "v99", null, false, true, java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        AgentRuntimeControlBootValidator validator = new AgentRuntimeControlBootValidator(
                store,
                new AgentPolicyRegistry(properties())
        );

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    @Test
    void unknownCandidateVersionFailsStartup() {
        AgentRuntimeControlStore store = mock(AgentRuntimeControlStore.class);
        when(store.require()).thenReturn(new AgentRuntimeControl(
                true, AgentPolicyVersion.V1, "ghost", false, true, java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        AgentRuntimeControlBootValidator validator = new AgentRuntimeControlBootValidator(
                store,
                new AgentPolicyRegistry(properties())
        );

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    @Test
    void registeredVersionsPass() {
        AgentRuntimeControlStore store = mock(AgentRuntimeControlStore.class);
        when(store.require()).thenReturn(AgentRuntimeControl.seed(java.time.Instant.parse("2026-09-18T14:00:00Z")));
        AgentRuntimeControlBootValidator validator = new AgentRuntimeControlBootValidator(
                store,
                new AgentPolicyRegistry(properties())
        );

        validator.run(null);
    }

    private static GuidanceProperties properties() {
        return new GuidanceProperties();
    }
}
