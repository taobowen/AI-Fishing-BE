package com.aifishing.guidance.contracts;

/**
 * Storage visibility for {@code agent_runs}. {@link #SHADOW} is replay/debug
 * only and must never participate in delivered guidance, cooldown, current,
 * horizon, or learning.
 */
public enum AgentRunVisibility {
    PRODUCTION,
    SHADOW
}
