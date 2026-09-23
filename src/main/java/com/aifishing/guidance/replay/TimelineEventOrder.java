package com.aifishing.guidance.replay;

import java.util.Comparator;

/**
 * Deterministic timeline order: {@code occurredAt} → {@link TimelineSource}
 * priority → source id. Source priority (documented for Task C/D):
 * {@code SESSION_EVENT}, {@code TRIGGER}, {@code AGENT_RUN}, {@code ADVICE},
 * {@code OBSERVED_ACTION}, {@code OUTCOME}, {@code HORIZON}, {@code CATCH}.
 */
public final class TimelineEventOrder {

    public static final Comparator<SessionTimelineEvent> COMPARATOR = Comparator
            .comparing(SessionTimelineEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(event -> event.source() == null ? Integer.MAX_VALUE : event.source().priority())
            .thenComparing(event -> event.id() == null ? "" : event.id().toString());

    private TimelineEventOrder() {
    }
}
