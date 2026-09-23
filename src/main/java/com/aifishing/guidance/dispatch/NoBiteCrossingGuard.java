package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * NO_BITE is written only on threshold crossing. Reset on BITE / FISH_ON /
 * leave FISHING. A finalized NO_BITE run does not re-arm while still above T.
 */
@Component
public class NoBiteCrossingGuard {

    private static final EnumSet<SessionEventType> RESET_SIGNALS =
            EnumSet.of(SessionEventType.BITE, SessionEventType.FISH_ON);

    private final SessionEventRepository sessionEventRepository;
    private final GuidanceTriggerOutboxRepository outboxRepository;

    public NoBiteCrossingGuard(
            SessionEventRepository sessionEventRepository,
            GuidanceTriggerOutboxRepository outboxRepository
    ) {
        this.sessionEventRepository = sessionEventRepository;
        this.outboxRepository = outboxRepository;
    }

    public boolean shouldEnqueue(
            FishingSession session,
            FishingSessionState state,
            TriggerRoutingDecision decision
    ) {
        if (decision == null || decision.primary() != GuidanceTrigger.NO_BITE_THRESHOLD) {
            return true;
        }
        if (session == null || state == null || state.fishing() == null) {
            return false;
        }
        FishingActivityState activity = state.fishing().activityState() == null
                ? FishingActivityState.UNKNOWN
                : state.fishing().activityState();
        if (activity != FishingActivityState.FISHING) {
            return false;
        }
        Instant resetAt = lastResetAt(session);
        return !alreadyEmittedSince(session.getId(), resetAt);
    }

    public Instant lastResetAt(FishingSession session) {
        Instant reset = session.getActivityStateSince() != null
                ? session.getActivityStateSince()
                : session.getStartedAt();
        for (SessionEventEntity event : sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
                session.getId(), RESET_SIGNALS
        )) {
            if (event.getOccurredAt() != null && (reset == null || event.getOccurredAt().isAfter(reset))) {
                reset = event.getOccurredAt();
            }
        }
        return reset == null ? Instant.EPOCH : reset;
    }

    public boolean alreadyEmittedSince(UUID fishingSessionId, Instant resetAt) {
        Instant since = resetAt == null ? Instant.EPOCH : resetAt;
        if (!sessionEventRepository
                .findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                        fishingSessionId, SessionEventType.NO_BITE, since)
                .isEmpty()) {
            return true;
        }
        List<GuidanceTriggerOutboxEntity> rows =
                outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(fishingSessionId, since);
        for (GuidanceTriggerOutboxEntity row : rows) {
            if (row.getPrimaryTrigger() == GuidanceTrigger.NO_BITE_THRESHOLD) {
                return true;
            }
            if (row.getRelatedTriggers() != null && row.getRelatedTriggers().contains(GuidanceTrigger.NO_BITE_THRESHOLD)) {
                return true;
            }
        }
        return false;
    }
}
