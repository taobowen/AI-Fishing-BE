package com.aifishing.guidance.learning;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.SessionSummary;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.LureEventEntity;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.SessionSummaryEntity;
import com.aifishing.guidance.persistence.SessionSummaryRepository;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SessionSummaryJobHandler implements LearningJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryJobHandler.class);

    private final FishingSessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final CatchEventRepository catchEventRepository;
    private final LureEventRepository lureEventRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final SessionSummaryRepository summaryRepository;
    private final SessionSummaryLlmClient llmClient;
    private final GuidanceProperties properties;
    private final Clock clock;

    public SessionSummaryJobHandler(
            FishingSessionRepository sessionRepository,
            SessionEventRepository sessionEventRepository,
            CatchEventRepository catchEventRepository,
            LureEventRepository lureEventRepository,
            AgentFeedbackRepository feedbackRepository,
            SessionSummaryRepository summaryRepository,
            SessionSummaryLlmClient llmClient,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.catchEventRepository = catchEventRepository;
        this.lureEventRepository = lureEventRepository;
        this.feedbackRepository = feedbackRepository;
        this.summaryRepository = summaryRepository;
        this.llmClient = llmClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.SESSION_SUMMARY;
    }

    @Override
    @Transactional
    public void handle(GuidanceLearningOutboxEntity job) {
        UUID sessionId = job.getFishingSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("SESSION_SUMMARY requires fishingSessionId");
        }
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Fishing session not found"));
        if (session.getStatus() != FishingSessionStatus.COMPLETED) {
            throw new IllegalStateException("SESSION_SUMMARY requires a COMPLETED session");
        }
        List<SessionEventEntity> events = sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        List<LureEventEntity> lures = lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        List<AgentFeedbackEntity> feedback = feedbackRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        List<String> facts = ExtractiveSessionFacts.keyFacts(
                events,
                catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(sessionId, CatchStatus.ACTIVE),
                lures,
                feedback
        );
        String extractive = ExtractiveSessionFacts.extractiveSummary(facts);
        Instant now = clock.instant();
        String summaryText = extractive;
        List<String> keyFacts = facts;
        if (shouldSummarizeWithLlm(facts, extractive)) {
            try {
                SessionSummary generated = llmClient.summarize(facts, extractive);
                if (generated != null && generated.summaryText() != null && !generated.summaryText().isBlank()) {
                    summaryText = generated.summaryText();
                    if (generated.keyFacts() != null && !generated.keyFacts().isEmpty()) {
                        keyFacts = generated.keyFacts();
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Session summary LLM failed; keeping extractive text: {}", ex.getMessage());
            }
        }
        SessionSummaryEntity row = summaryRepository.findById(sessionId).orElseGet(SessionSummaryEntity::new);
        row.setFishingSessionId(sessionId);
        row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        row.setSummaryText(summaryText);
        row.setKeyFacts(keyFacts);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        summaryRepository.save(row);
    }

    boolean shouldSummarizeWithLlm(List<String> facts, String extractive) {
        if (properties.getRuntimeMode() != GuidanceProperties.RuntimeMode.OPENAI) {
            return false;
        }
        GuidanceProperties.Memory memory = properties.getMemory();
        int factCount = facts == null ? 0 : facts.size();
        int chars = extractive == null ? 0 : extractive.length();
        return factCount >= memory.getSessionSummaryLlmMinFacts()
                || chars >= memory.getSessionSummaryLlmMinChars();
    }
}
