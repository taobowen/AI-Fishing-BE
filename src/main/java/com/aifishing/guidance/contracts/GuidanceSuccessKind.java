package com.aifishing.guidance.contracts;

/**
 * Strict guidance success is {@link #FISH_ON_SUCCESS} only.
 * {@link #BITE_SIGNAL_ONLY} is a positive fish signal and must not be folded into
 * {@link #NO_FISH_SIGNAL}. Landing is a separate analytic, not a member.
 */
public enum GuidanceSuccessKind {
    FISH_ON_SUCCESS,
    BITE_SIGNAL_ONLY,
    NO_FISH_SIGNAL,
    NOT_FOLLOWED,
    UNATTRIBUTED
}
