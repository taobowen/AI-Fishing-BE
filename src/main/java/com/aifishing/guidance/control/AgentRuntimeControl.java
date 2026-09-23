package com.aifishing.guidance.control;

import com.aifishing.guidance.versions.AgentPolicyVersion;

import java.time.Instant;

/**
 * Singleton live runtime switch. YAML {@link com.aifishing.guidance.versions.AgentPolicyRegistry}
 * lists available versions; this row selects production/candidate and gates.
 */
public record AgentRuntimeControl(
        boolean agentEnabled,
        String productionVersion,
        String candidateVersion,
        boolean shadowEnabled,
        boolean learningEnabled,
        Instant updatedAt
) {
    public static final short SINGLETON_ID = 1;

    public static AgentRuntimeControl seed(Instant updatedAt) {
        return new AgentRuntimeControl(true, AgentPolicyVersion.V1, null, false, true, updatedAt);
    }

    /**
     * Live candidate replay beside production. Kill switch overrides shadow off.
     */
    public boolean liveShadowActive() {
        return agentEnabled
                && shadowEnabled
                && candidateVersion != null
                && !candidateVersion.isBlank();
    }
}
