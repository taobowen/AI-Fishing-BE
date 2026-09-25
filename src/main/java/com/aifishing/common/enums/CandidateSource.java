package com.aifishing.common.enums;

/**
 * Provenance of a planned stop candidate.
 * User stops for balance accounting are {@link #REQUIRED} + {@link #TEMPLATE}.
 */
public enum CandidateSource {
    REQUIRED,
    TEMPLATE,
    AI;

    public static CandidateSource orAi(CandidateSource source) {
        return source == null ? AI : source;
    }

    public boolean isUserStop() {
        return this == REQUIRED || this == TEMPLATE;
    }
}
