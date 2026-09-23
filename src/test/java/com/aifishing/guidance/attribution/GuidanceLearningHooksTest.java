package com.aifishing.guidance.attribution;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.learning.GuidanceLearningOutboxService;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GuidanceLearningHooksTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:05:00Z");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FEEDBACK = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DELIVERED = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private OutcomeAttributor outcomeAttributor;
    @Mock
    private UserActionObserver userActionObserver;
    @Mock
    private SessionEventWriter sessionEventWriter;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private GuidanceLearningOutboxService learningOutboxService;

    private GuidanceLearningHooks hooks;

    @BeforeEach
    void setUp() {
        hooks = new GuidanceLearningHooks(
                outcomeAttributor,
                userActionObserver,
                sessionEventWriter,
                sessionEventRepository,
                learningOutboxService,
                new GuidanceProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acknowledgedWritesAdviceAcknowledgedAndSkipsPreferenceUpdate() {
        hooks.onFeedback(
                SESSION,
                USER,
                deliveredEntity(),
                stay(),
                FeedbackStatus.ACKNOWLEDGED,
                null,
                null,
                FEEDBACK
        );

        ArgumentCaptor<SessionEvent> event = ArgumentCaptor.forClass(SessionEvent.class);
        verify(sessionEventWriter).writeAudit(eq(SESSION), event.capture());
        assertThat(event.getValue().type()).isEqualTo(SessionEventType.ADVICE_ACKNOWLEDGED);
        assertThat(event.getValue().payload().get("status")).isEqualTo(FeedbackStatus.ACKNOWLEDGED.name());

        verify(userActionObserver).observe(
                eq(SESSION),
                any(),
                eq(SessionEventType.ADVICE_ACKNOWLEDGED),
                eq(NOW),
                any()
        );
        verify(learningOutboxService, never()).enqueue(
                eq(SESSION),
                eq(LearningJobType.PREFERENCE_UPDATE),
                any(),
                any()
        );
        verify(learningOutboxService).enqueue(
                eq(SESSION),
                eq(LearningJobType.REFLECTION_EVAL),
                eq("reflection-eval:feedback:" + FEEDBACK),
                any()
        );
    }

    @Test
    void acceptedStillWritesAdviceAcceptedWithoutPreferenceUpdate() {
        hooks.onFeedback(
                SESSION,
                USER,
                deliveredEntity(),
                stay(),
                FeedbackStatus.ACCEPTED,
                null,
                null,
                FEEDBACK
        );

        ArgumentCaptor<SessionEvent> event = ArgumentCaptor.forClass(SessionEvent.class);
        verify(sessionEventWriter).writeAudit(eq(SESSION), event.capture());
        assertThat(event.getValue().type()).isEqualTo(SessionEventType.ADVICE_ACCEPTED);
        verify(userActionObserver).observe(
                eq(SESSION),
                any(),
                eq(SessionEventType.ADVICE_ACCEPTED),
                eq(NOW),
                any()
        );
        verify(learningOutboxService, never()).enqueue(
                eq(SESSION),
                eq(LearningJobType.PREFERENCE_UPDATE),
                any(),
                any()
        );
    }

    @Test
    void rejectedStillWritesAdviceRejectedAndPreferenceUpdate() {
        hooks.onFeedback(
                SESSION,
                USER,
                deliveredEntity(),
                stay(),
                FeedbackStatus.REJECTED,
                GuidanceRejectReason.TOO_FAR,
                "too far",
                FEEDBACK
        );

        ArgumentCaptor<SessionEvent> event = ArgumentCaptor.forClass(SessionEvent.class);
        verify(sessionEventWriter).writeAudit(eq(SESSION), event.capture());
        assertThat(event.getValue().type()).isEqualTo(SessionEventType.ADVICE_REJECTED);
        verify(learningOutboxService).enqueue(
                eq(SESSION),
                eq(LearningJobType.PREFERENCE_UPDATE),
                eq("preference-update:" + FEEDBACK),
                any()
        );
    }

    private static AgentDeliveredDecisionEntity deliveredEntity() {
        AgentDeliveredDecisionEntity entity = new AgentDeliveredDecisionEntity();
        entity.setId(DELIVERED);
        entity.setRunId(GuidancePhase2Fixtures.DECISION_ID);
        entity.setCreatedAt(NOW.minusSeconds(60));
        return entity;
    }

    private static DeliveredDecision stay() {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.STAY,
                null,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("TEST"),
                "Stay",
                List.of(),
                false,
                null,
                0.8
        );
    }
}
