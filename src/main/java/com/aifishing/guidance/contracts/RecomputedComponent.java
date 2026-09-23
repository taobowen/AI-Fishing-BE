package com.aifishing.guidance.contracts;

/**
 * Closed set of components that {@link ReplayMode#COMPONENT_RECOMPUTE} may rerun.
 * Unlisted components stay on the frozen record.
 */
public enum RecomputedComponent {
    MEMORY_RETRIEVAL,
    TOOLS,
    CONTEXT,
    ENVIRONMENT,
    SAFETY,
    MODEL,
    PROMPT
}
