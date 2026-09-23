package com.aifishing.guidance.learning;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.SessionSummary;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.SessionSummaryEntity;
import com.aifishing.guidance.persistence.SessionSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionSummaryJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private CatchEventRepository catchEventRepository;
    @Mock
    private LureEventRepository lureEventRepository;
    @Mock
    private AgentFeedbackRepository feedbackRepository;
    @Mock
    private SessionSummaryRepository summaryRepository;
    @Mock
    private SessionSummaryLlmClient llmClient;

    private GuidanceProperties properties;
    private SessionSummaryJobHandler handler;

    @BeforeEach
    void setUp() {
        properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        handler = new SessionSummaryJobHandler(
                sessionRepository,
                sessionEventRepository,
                catchEventRepository,
                lureEventRepository,
                feedbackRepository,
                summaryRepository,
                llmClient,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        FishingSession session = new FishingSession();
        session.setId(SESSION);
        session.setStatus(FishingSessionStatus.COMPLETED);
        when(sessionRepository.findById(SESSION)).thenReturn(Optional.of(session));
        when(sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION))
                .thenReturn(List.of(event(SessionEventType.FISH_ON), event(SessionEventType.SESSION_COMPLETED)));
        when(catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(SESSION, CatchStatus.ACTIVE))
                .thenReturn(List.of());
        when(lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION)).thenReturn(List.of());
        when(feedbackRepository.findByFishingSessionIdOrderByOccurredAtAsc(SESSION)).thenReturn(List.of());
        when(summaryRepository.findById(SESSION)).thenReturn(Optional.empty());
    }

    @Test
    void deterministicModeWritesExtractiveSummaryWithoutLlm() {
        handler.handle(job());

        ArgumentCaptor<SessionSummaryEntity> captor = ArgumentCaptor.forClass(SessionSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getFishingSessionId()).isEqualTo(SESSION);
        assertThat(captor.getValue().getSummaryText()).contains("fish-on");
        assertThat(captor.getValue().getKeyFacts()).isNotEmpty();
        verify(llmClient, never()).summarize(any(), any());
    }

    @Test
    void openaiBelowThresholdStillUsesExtractive() {
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);

        handler.handle(job());

        verify(llmClient, never()).summarize(any(), any());
        verify(summaryRepository).save(any());
    }

    @Test
    void openaiOverThresholdCallsLlmOnce() {
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        properties.getMemory().setSessionSummaryLlmMinFacts(1);
        when(llmClient.summarize(any(), any())).thenReturn(new SessionSummary(
                null, SESSION, "Quiet but one fish-on.", List.of("1 fish-on"), NOW
        ));

        handler.handle(job());

        verify(llmClient).summarize(any(), any());
        ArgumentCaptor<SessionSummaryEntity> captor = ArgumentCaptor.forClass(SessionSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getSummaryText()).isEqualTo("Quiet but one fish-on.");
    }

    @Test
    void shouldSummarizeWithLlmRequiresOpenaiAndThreshold() {
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        assertThat(handler.shouldSummarizeWithLlm(List.of("a", "b", "c", "d", "e", "f", "g", "h"), "x".repeat(900)))
                .isFalse();

        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        assertThat(handler.shouldSummarizeWithLlm(List.of("one"), "short")).isFalse();
        assertThat(handler.shouldSummarizeWithLlm(List.of("a", "b", "c", "d", "e", "f", "g", "h"), "short")).isTrue();
        assertThat(handler.shouldSummarizeWithLlm(List.of("one"), "x".repeat(800))).isTrue();
    }

    private static GuidanceLearningOutboxEntity job() {
        GuidanceLearningOutboxEntity entity = new GuidanceLearningOutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setFishingSessionId(SESSION);
        entity.setJobType(LearningJobType.SESSION_SUMMARY);
        entity.setPayload(Map.of("userId", UUID.randomUUID().toString()));
        return entity;
    }

    private static SessionEventEntity event(SessionEventType type) {
        SessionEventEntity entity = new SessionEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setFishingSessionId(SESSION);
        entity.setType(type);
        entity.setOccurredAt(NOW);
        entity.setPayload(Map.of());
        return entity;
    }
}
