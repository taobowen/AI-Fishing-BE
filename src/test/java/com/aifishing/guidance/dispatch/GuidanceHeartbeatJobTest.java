package com.aifishing.guidance.dispatch;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionGuidanceMode;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.spi.DerivedTriggerEvaluator;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceHeartbeatJobTest {

    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private FishingSessionStateBuilder stateBuilder;
    @Mock
    private DerivedTriggerEvaluator evaluator;
    @Mock
    private NoBiteCrossingGuard crossingGuard;
    @Mock
    private GuidanceTriggerOutboxService outboxService;
    @Mock
    private SessionEventWriter sessionEventWriter;
    @Mock
    private Clock clock;
    @InjectMocks
    private GuidanceHeartbeatJob job;

    @Test
    void navigationOnlySessionsAreSkipped() {
        FishingSession session = new FishingSession();
        session.setId(UUID.randomUUID());
        session.setGuidanceMode(SessionGuidanceMode.NAVIGATION_ONLY);
        when(sessionRepository.findByStatusAndActivityState(
                FishingSessionStatus.ACTIVE, FishingActivityState.FISHING
        )).thenReturn(List.of(session));

        assertThat(job.tick()).isZero();
        verify(evaluator, never()).evaluate(org.mockito.ArgumentMatchers.any());
        verify(outboxService, never()).upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
