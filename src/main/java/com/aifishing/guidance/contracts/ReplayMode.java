package com.aifishing.guidance.contracts;

/**
 * FROZEN_REPLAY uses recorded state, memory, tool observations, and clock.
 * COMPONENT_RECOMPUTE must list every component that is rerun.
 */
public enum ReplayMode {
    FROZEN_REPLAY,
    COMPONENT_RECOMPUTE
}
