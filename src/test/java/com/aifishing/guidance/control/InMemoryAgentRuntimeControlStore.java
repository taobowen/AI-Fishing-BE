package com.aifishing.guidance.control;

import java.time.Instant;

/**
 * Test double. Does not validate catalog ids; production uses
 * {@code JpaAgentRuntimeControlStore}.
 */
public final class InMemoryAgentRuntimeControlStore implements AgentRuntimeControlStore {

    private AgentRuntimeControl control;

    public InMemoryAgentRuntimeControlStore() {
        this(AgentRuntimeControl.seed(Instant.parse("2026-09-18T14:00:00Z")));
    }

    public InMemoryAgentRuntimeControlStore(AgentRuntimeControl control) {
        this.control = control;
    }

    public void set(AgentRuntimeControl control) {
        this.control = control;
    }

    @Override
    public AgentRuntimeControl load() {
        return control;
    }

    @Override
    public AgentRuntimeControl require() {
        if (control == null) {
            throw new IllegalStateException("agent_runtime_control singleton row is missing");
        }
        return control;
    }

    @Override
    public AgentRuntimeControl update(AgentRuntimeControlPatch patch) {
        AgentRuntimeControl current = require();
        boolean agentEnabled = patch.agentEnabled() == null ? current.agentEnabled() : patch.agentEnabled();
        String production = patch.productionVersion() == null ? current.productionVersion() : patch.productionVersion();
        String candidate = current.candidateVersion();
        if (patch.candidateVersion() != null) {
            candidate = patch.candidateVersion().isBlank() ? null : patch.candidateVersion().trim();
        }
        boolean shadow = patch.shadowEnabled() == null ? current.shadowEnabled() : patch.shadowEnabled();
        boolean learning = patch.learningEnabled() == null ? current.learningEnabled() : patch.learningEnabled();
        control = new AgentRuntimeControl(
                agentEnabled,
                production,
                candidate,
                shadow,
                learning,
                Instant.parse("2026-09-18T15:00:00Z")
        );
        return control;
    }
}
