package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTriggerRouterTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");

    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private GuidanceTriggerOutboxRepository outboxRepository;

    private DefaultTriggerRouter router;

    @BeforeEach
    void setUp() {
        router = new DefaultTriggerRouter(
                new GuidanceProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                sessionEventRepository,
                agentRunRepository,
                outboxRepository
        );
    }

    @Test
    void singleBiteDoesNotEnqueueAndArriveDoes() {
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.BITE), any()
        )).thenReturn(List.of(biteEntity()));

        assertThat(router.route(event(SessionEventType.BITE, Map.of()), GuidancePhase2Fixtures.safeState())).isEmpty();
        assertThat(router.route(event(SessionEventType.WAYPOINT_ENTERED, Map.of("waypointId", SESSION_ID.toString())), GuidancePhase2Fixtures.safeState()))
                .hasValueSatisfying(decision -> assertThat(decision.primary()).isEqualTo(GuidanceTrigger.WAYPOINT_REACHED));
        assertThat(router.route(event(SessionEventType.CATCH_CREATED, Map.of()), GuidancePhase2Fixtures.safeState())).isEmpty();
        assertThat(router.route(event(SessionEventType.LURE_CHANGED, Map.of()), GuidancePhase2Fixtures.safeState())).isEmpty();
        assertThat(router.route(event(SessionEventType.SESSION_PAUSED, Map.of()), GuidancePhase2Fixtures.safeState())).isEmpty();
        assertThat(router.route(event(SessionEventType.WAYPOINT_LEFT, Map.of("waypointId", SESSION_ID.toString(), "skipped", true)), GuidancePhase2Fixtures.safeState()))
                .isEmpty();
        assertThat(router.route(event(SessionEventType.WAYPOINT_LEFT, Map.of("waypointId", SESSION_ID.toString(), "completed", true)), GuidancePhase2Fixtures.safeState()))
                .hasValueSatisfying(decision -> assertThat(decision.primary()).isEqualTo(GuidanceTrigger.PLAN_STEP_COMPLETED));
        assertThat(router.route(event(SessionEventType.USER_STARTED_AD_HOC_FISHING, Map.of()), GuidancePhase2Fixtures.safeState()))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.primary()).isEqualTo(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
                    assertThat(decision.reasonCodes()).containsExactly("USER_STARTED_AD_HOC_FISHING");
                });
        assertThat(router.route(event(SessionEventType.USER_ENDED_AD_HOC_FISHING, Map.of()), GuidancePhase2Fixtures.safeState()))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.primary()).isEqualTo(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING);
                    assertThat(decision.reasonCodes()).containsExactly("USER_ENDED_AD_HOC_FISHING");
                });
        assertThat(router.route(event(SessionEventType.ADVICE_ACKNOWLEDGED, Map.of()), GuidancePhase2Fixtures.safeState()))
                .isEmpty();
    }

    @Test
    void fishOnCooldownIgnoresShadowRuns() {
        stubEmptyOutbox();
        AgentRunEntity shadow = new AgentRunEntity();
        shadow.setTrigger(GuidanceTrigger.FISH_ON);
        shadow.setVisibility(AgentRunVisibility.SHADOW);
        shadow.setStatus(AgentRunStatus.COMPLETED);
        shadow.setStartedAt(NOW);
        when(agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                SESSION_ID, AgentRunVisibility.PRODUCTION
        )).thenReturn(List.of());

        assertThat(router.route(event(SessionEventType.FISH_ON, Map.of()), GuidancePhase2Fixtures.safeState()))
                .hasValueSatisfying(decision -> assertThat(decision.primary()).isEqualTo(GuidanceTrigger.FISH_ON));
        verify(agentRunRepository, never()).findByFishingSessionIdOrderByStartedAtDesc(SESSION_ID);
    }

    @Test
    void fishOnCooldownHonorsProductionRuns() {
        stubEmptyOutbox();
        AgentRunEntity production = new AgentRunEntity();
        production.setTrigger(GuidanceTrigger.FISH_ON);
        production.setVisibility(AgentRunVisibility.PRODUCTION);
        production.setStatus(AgentRunStatus.COMPLETED);
        production.setStartedAt(NOW);
        when(agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                SESSION_ID, AgentRunVisibility.PRODUCTION
        )).thenReturn(List.of(production));

        assertThat(router.route(event(SessionEventType.FISH_ON, Map.of()), GuidancePhase2Fixtures.safeState()))
                .isEmpty();
    }

    private void stubEmptyOutbox() {
        when(outboxRepository.findByFishingSessionIdAndPrimaryTriggerAndCreatedAtGreaterThanEqual(
                eq(SESSION_ID), eq(GuidanceTrigger.FISH_ON), any()
        )).thenReturn(List.of());
        when(outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(eq(SESSION_ID), any()))
                .thenReturn(List.of());
    }

    private static SessionEvent event(SessionEventType type, Map<String, Object> payload) {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                type,
                NOW,
                EventSource.CLIENT,
                type.name(),
                payload,
                null
        );
    }

    private static SessionEventEntity biteEntity() {
        SessionEventEntity entity = new SessionEventEntity();
        entity.setType(SessionEventType.BITE);
        entity.setOccurredAt(NOW);
        return entity;
    }
}
