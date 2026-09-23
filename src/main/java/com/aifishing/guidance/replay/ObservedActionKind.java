package com.aifishing.guidance.replay;

/**
 * Derived observation status. {@link #ACKNOWLEDGED} is never {@link #FOLLOWED}.
 * {@link #UNKNOWN} means no stored {@code user_action_events} yet.
 */
public enum ObservedActionKind {
    UNKNOWN,
    FOLLOWED,
    NOT_FOLLOWED,
    ACKNOWLEDGED
}
