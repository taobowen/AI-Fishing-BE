package com.aifishing.fishingsession.domain;

public enum SessionGuidanceMode {
    NAVIGATION_ONLY,
    AGENT_GUIDED;

    public static SessionGuidanceMode orDefault(SessionGuidanceMode mode) {
        return mode == null ? AGENT_GUIDED : mode;
    }

    public boolean routesAgent() {
        return this == AGENT_GUIDED;
    }
}
