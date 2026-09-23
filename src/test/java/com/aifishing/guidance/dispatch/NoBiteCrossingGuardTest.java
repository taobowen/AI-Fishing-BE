package com.aifishing.guidance.dispatch;

import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoBiteCrossingGuardTest {

    private static final Instant SINCE = Instant.parse("2026-09-16T13:00:00Z");

    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private GuidanceTriggerOutboxRepository outboxRepository;

    private NoBiteCrossingGuard guard;
    private FishingSession session;

    @BeforeEach
    void setUp() {
        guard = new NoBiteCrossingGuard(sessionEventRepository, outboxRepository);
        session = new FishingSession();
        session.setId(SESSION_ID);
        session.setActivityState(FishingActivityState.FISHING);
        session.setActivityStateSince(SINCE);
        session.setStartedAt(SINCE);
    }

    @Test
    void firstCrossingEnqueuesAndDuplicateDoesNot() {
        when(sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.NO_BITE), any()
        )).thenReturn(List.of());
        when(outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(eq(SESSION_ID), any()))
                .thenReturn(List.of());

        TriggerRoutingDecision decision = new TriggerRoutingDecision(
                GuidanceTrigger.NO_BITE_THRESHOLD, List.of(), List.of("NO_BITE_THRESHOLD_CROSSED")
        );
        assertThat(guard.shouldEnqueue(session, GuidancePhase2Fixtures.safeState(), decision)).isTrue();

        SessionEventEntity audit = new SessionEventEntity();
        audit.setType(SessionEventType.NO_BITE);
        audit.setOccurredAt(SINCE.plusSeconds(60));
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.NO_BITE), any()
        )).thenReturn(List.of(audit));
        assertThat(guard.shouldEnqueue(session, GuidancePhase2Fixtures.safeState(), decision)).isFalse();
    }

    @Test
    void biteAfterCrossingResetsTheLock() {
        SessionEventEntity bite = new SessionEventEntity();
        bite.setType(SessionEventType.BITE);
        bite.setOccurredAt(SINCE.plusSeconds(120));
        when(sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of(bite));
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.NO_BITE), any()
        )).thenReturn(List.of());
        when(outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(eq(SESSION_ID), any()))
                .thenReturn(List.of());

        TriggerRoutingDecision decision = new TriggerRoutingDecision(
                GuidanceTrigger.NO_BITE_THRESHOLD, List.of(), List.of("NO_BITE_THRESHOLD_CROSSED")
        );
        assertThat(guard.shouldEnqueue(session, GuidancePhase2Fixtures.safeState(), decision)).isTrue();
    }

    @Test
    void existingOutboxNoBiteBlocksUntilReset() {
        when(sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.NO_BITE), any()
        )).thenReturn(List.of());
        GuidanceTriggerOutboxEntity row = new GuidanceTriggerOutboxEntity();
        row.setPrimaryTrigger(GuidanceTrigger.SAFETY_STATE_CHANGED);
        row.setRelatedTriggers(List.of(GuidanceTrigger.NO_BITE_THRESHOLD));
        when(outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(eq(SESSION_ID), any()))
                .thenReturn(List.of(row));

        TriggerRoutingDecision decision = new TriggerRoutingDecision(
                GuidanceTrigger.NO_BITE_THRESHOLD, List.of(), List.of("NO_BITE_THRESHOLD_CROSSED")
        );
        assertThat(guard.shouldEnqueue(session, GuidancePhase2Fixtures.safeState(), decision)).isFalse();
    }
}
