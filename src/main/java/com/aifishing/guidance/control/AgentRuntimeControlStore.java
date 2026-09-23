package com.aifishing.guidance.control;

/**
 * Reads and writes the singleton {@link AgentRuntimeControl} row.
 * Writes validate version ids through {@code AgentPolicyRegistry.require}.
 * Implementations must not rewrite {@code agent_runs}.
 */
public interface AgentRuntimeControlStore {

    AgentRuntimeControl load();

    AgentRuntimeControl require();

    AgentRuntimeControl update(AgentRuntimeControlPatch patch);
}
