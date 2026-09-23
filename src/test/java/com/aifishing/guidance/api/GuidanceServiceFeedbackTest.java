package com.aifishing.guidance.api;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.attribution.AdviceLifecycleWriter;
import com.aifishing.guidance.attribution.GuidanceLearningHooks;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceFeedbackRequest;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.horizon.GuidanceHorizonWriter;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.guidance.spi.FishingAgentFacade;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceServiceFeedbackTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:05:00Z");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RUN = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DECISION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FEEDBACK = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private CurrentUser currentUser;
    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionEventWriter sessionEventWriter;
    @Mock
    private FishingAgentFacade facade;
    @Mock
    private DecisionPersistence decisionPersistence;
    @Mock
    private GuidanceHorizonWriter horizonWriter;
    @Mock
    private AgentDeliveredDecisionRepository deliveredRepository;
    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private AgentFeedbackRepository feedbackRepository;
    @Mock
    private AdviceLifecycleWriter adviceLifecycleWriter;
    @Mock
    private GuidanceLearningHooks learningHooks;

    private GuidanceService service;

    @BeforeEach
    void setUp() {
        service = new GuidanceService(
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC),
                sessionRepository,
                sessionEventWriter,
                facade,
                decisionPersistence,
                horizonWriter,
                deliveredRepository,
                agentRunRepository,
                feedbackRepository,
                adviceLifecycleWriter,
                learningHooks
        );
    }

    @Test
    void acknowledgedDoesNotRequireRejectReason() {
        stubOwnedDecision();
        when(feedbackRepository.save(any())).thenAnswer(invocation -> {
            AgentFeedbackEntity entity = invocation.getArgument(0);
            entity.setId(FEEDBACK);
            return entity;
        });

        service.submitFeedback(SESSION, DECISION, new GuidanceFeedbackRequest(FeedbackStatus.ACKNOWLEDGED, null, null));

        ArgumentCaptor<AgentFeedbackEntity> saved = ArgumentCaptor.forClass(AgentFeedbackEntity.class);
        verify(feedbackRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(FeedbackStatus.ACKNOWLEDGED);
        assertThat(saved.getValue().getRejectReason()).isNull();
        assertThat(GuidanceRejectReason.values()).isNotEmpty();
        verify(learningHooks).onFeedback(
                eq(SESSION),
                eq(USER),
                any(),
                any(),
                eq(FeedbackStatus.ACKNOWLEDGED),
                isNull(),
                isNull(),
                eq(FEEDBACK)
        );
    }

    @Test
    void rejectedWithoutReasonStillDefaultsToUnspecified() {
        stubOwnedDecision();
        when(feedbackRepository.save(any())).thenAnswer(invocation -> {
            AgentFeedbackEntity entity = invocation.getArgument(0);
            entity.setId(FEEDBACK);
            return entity;
        });

        service.submitFeedback(SESSION, DECISION, new GuidanceFeedbackRequest(FeedbackStatus.REJECTED, null, null));

        ArgumentCaptor<AgentFeedbackEntity> saved = ArgumentCaptor.forClass(AgentFeedbackEntity.class);
        verify(feedbackRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(FeedbackStatus.REJECTED);
        assertThat(saved.getValue().getRejectReason()).isEqualTo(GuidanceRejectReason.UNSPECIFIED.name());
        verify(learningHooks).onFeedback(
                eq(SESSION),
                eq(USER),
                any(),
                any(),
                eq(FeedbackStatus.REJECTED),
                eq(GuidanceRejectReason.UNSPECIFIED),
                isNull(),
                eq(FEEDBACK)
        );
    }

    @Test
    void killSwitchReturnsLastRealCurrentWithoutHorizonOrAdvice() {
        when(currentUser.id()).thenReturn(USER);
        FishingSession session = new FishingSession();
        session.setId(SESSION);
        session.setUserId(USER);
        when(sessionRepository.findByIdAndUserId(SESSION, USER)).thenReturn(Optional.of(session));
        DeliveredDecision lastReal = stay();
        DeliveredDecision continuation = com.aifishing.guidance.runtime.KillSwitchContinuation.from(RUN, null);
        when(facade.run(eq(SESSION), eq(com.aifishing.guidance.contracts.GuidanceTrigger.USER_REQUEST), any()))
                .thenReturn(new com.aifishing.guidance.contracts.AgentRunResult(
                        GuidanceSchemaVersion.VALUE,
                        RUN,
                        com.aifishing.guidance.contracts.AgentRunStatus.FALLBACK,
                        null,
                        List.of(),
                        null,
                        null,
                        continuation
                ));
        when(decisionPersistence.current(SESSION)).thenReturn(Optional.of(lastReal));

        var response = service.requestDecision(SESSION, null);

        assertThat(response.decision()).isEqualTo(lastReal);
        verify(horizonWriter, never()).writeAfterDelivered(any(), any(com.aifishing.guidance.contracts.AgentRunResult.class));
        verify(adviceLifecycleWriter, never()).writeCreated(any(), any(com.aifishing.guidance.contracts.AgentRunResult.class));
    }

    @Test
    void killSwitchWithoutPriorAdviceReturnsContinuationWithoutPersistingAdvice() {
        when(currentUser.id()).thenReturn(USER);
        FishingSession session = new FishingSession();
        session.setId(SESSION);
        session.setUserId(USER);
        when(sessionRepository.findByIdAndUserId(SESSION, USER)).thenReturn(Optional.of(session));
        DeliveredDecision continuation = com.aifishing.guidance.runtime.KillSwitchContinuation.from(RUN, null);
        when(facade.run(eq(SESSION), eq(com.aifishing.guidance.contracts.GuidanceTrigger.USER_REQUEST), any()))
                .thenReturn(new com.aifishing.guidance.contracts.AgentRunResult(
                        GuidanceSchemaVersion.VALUE,
                        RUN,
                        com.aifishing.guidance.contracts.AgentRunStatus.FALLBACK,
                        null,
                        List.of(),
                        null,
                        null,
                        continuation
                ));
        when(decisionPersistence.current(SESSION)).thenReturn(Optional.empty());

        var response = service.requestDecision(SESSION, null);

        assertThat(response.decision().fallbackReason()).isEqualTo(com.aifishing.guidance.runtime.GuidanceFallback.KILL_SWITCH);
        assertThat(response.decision().primaryAction()).isEqualTo(GuidanceAction.RETURN);
        verify(horizonWriter, never()).writeAfterDelivered(any(), any(com.aifishing.guidance.contracts.AgentRunResult.class));
        verify(adviceLifecycleWriter, never()).writeCreated(any(), any(com.aifishing.guidance.contracts.AgentRunResult.class));
    }

    @Test
    void shadowRunCannotReceiveProductionFeedbackOrLearning() {
        when(currentUser.id()).thenReturn(USER);
        FishingSession session = new FishingSession();
        session.setId(SESSION);
        session.setUserId(USER);
        when(sessionRepository.findByIdAndUserId(SESSION, USER)).thenReturn(Optional.of(session));

        AgentDeliveredDecisionEntity delivered = new AgentDeliveredDecisionEntity();
        delivered.setId(DECISION);
        delivered.setRunId(RUN);
        delivered.setCreatedAt(NOW.minusSeconds(30));
        delivered.setDecision(GuidanceContracts.mapper().convertValue(stay(), Map.class));
        when(deliveredRepository.findFirstByRunId(DECISION)).thenReturn(Optional.of(delivered));

        AgentRunEntity shadow = new AgentRunEntity();
        shadow.setId(RUN);
        shadow.setFishingSessionId(SESSION);
        shadow.setVisibility(AgentRunVisibility.SHADOW);
        when(agentRunRepository.findById(RUN)).thenReturn(Optional.of(shadow));

        assertThatThrownBy(() -> service.submitFeedback(
                SESSION, DECISION, new GuidanceFeedbackRequest(FeedbackStatus.ACKNOWLEDGED, null, null)
        )).isInstanceOf(NotFoundException.class);
        verify(feedbackRepository, never()).save(any());
        verify(learningHooks, never()).onFeedback(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void stubOwnedDecision() {
        when(currentUser.id()).thenReturn(USER);
        FishingSession session = new FishingSession();
        session.setId(SESSION);
        session.setUserId(USER);
        when(sessionRepository.findByIdAndUserId(SESSION, USER)).thenReturn(Optional.of(session));

        AgentDeliveredDecisionEntity delivered = new AgentDeliveredDecisionEntity();
        delivered.setId(DECISION);
        delivered.setRunId(RUN);
        delivered.setCreatedAt(NOW.minusSeconds(30));
        delivered.setDecision(GuidanceContracts.mapper().convertValue(stay(), Map.class));
        when(deliveredRepository.findFirstByRunId(DECISION)).thenReturn(Optional.of(delivered));

        AgentRunEntity run = new AgentRunEntity();
        run.setId(RUN);
        run.setFishingSessionId(SESSION);
        when(agentRunRepository.findById(RUN)).thenReturn(Optional.of(run));
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
