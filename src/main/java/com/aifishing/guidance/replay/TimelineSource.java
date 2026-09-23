package com.aifishing.guidance.replay;

/**
 * Stable timeline source priority for same-{@code occurredAt} ties.
 * Order is: SESSION_EVENT, TRIGGER, AGENT_RUN, ADVICE, OBSERVED_ACTION,
 * OUTCOME, HORIZON, CATCH. Third key is source id.
 */
public enum TimelineSource {
    SESSION_EVENT,
    TRIGGER,
    AGENT_RUN,
    ADVICE,
    OBSERVED_ACTION,
    OUTCOME,
    HORIZON,
    CATCH;

    public int priority() {
        return ordinal();
    }
}
