package com.aifishing.guidance.replay;

/**
 * Timeline dispatch status for {@code AGENT_TRIGGER}. {@code COALESCED} is used
 * when {@code relatedTriggers} were merged into the outbox row. {@code DROPPED}
 * is omitted: cooldown skips are not persisted as outbox rows.
 */
public enum TriggerDispatchStatus {
    PENDING,
    CLAIMED,
    DONE,
    FAILED,
    COALESCED
}
