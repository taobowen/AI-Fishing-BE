package com.aifishing.common.enums;

/**
 * Trip planning mode. Null/absent on old clients is treated as {@link #AI}.
 */
public enum PlanningMode {
    AI,
    HYBRID,
    CUSTOM;

    public static PlanningMode orAi(PlanningMode mode) {
        return mode == null ? AI : mode;
    }

    public boolean requiresTemplate() {
        return this == HYBRID || this == CUSTOM;
    }
}
