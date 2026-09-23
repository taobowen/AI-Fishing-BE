package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.InferredUserPreference;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.UserFishingPreferences;
import com.aifishing.guidance.persistence.AgentReflectionEntity;
import com.aifishing.guidance.persistence.AgentReflectionRepository;
import com.aifishing.guidance.persistence.InferredUserPreferenceEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceRepository;
import com.aifishing.guidance.persistence.SemanticMemoryEntity;
import com.aifishing.guidance.persistence.SemanticMemoryRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.SessionSummaryEntity;
import com.aifishing.guidance.persistence.SessionSummaryRepository;
import com.aifishing.guidance.persistence.UserFishingPreferencesEntity;
import com.aifishing.guidance.persistence.UserFishingPreferencesRepository;
import com.aifishing.guidance.spi.MemoryRetrievalService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class DefaultMemoryRetrievalService implements MemoryRetrievalService {

    private final UserFishingPreferencesRepository explicitRepository;
    private final InferredUserPreferenceRepository inferredRepository;
    private final SessionSummaryRepository summaryRepository;
    private final AgentReflectionRepository reflectionRepository;
    private final SemanticMemoryRepository semanticMemoryRepository;
    private final SessionEventRepository sessionEventRepository;
    private final OutcomeAttributionReads outcomeAttributionReads;
    private final GuidanceProperties properties;
    private final Clock clock;

    public DefaultMemoryRetrievalService(
            UserFishingPreferencesRepository explicitRepository,
            InferredUserPreferenceRepository inferredRepository,
            SessionSummaryRepository summaryRepository,
            AgentReflectionRepository reflectionRepository,
            SemanticMemoryRepository semanticMemoryRepository,
            SessionEventRepository sessionEventRepository,
            OutcomeAttributionReads outcomeAttributionReads,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.explicitRepository = explicitRepository;
        this.inferredRepository = inferredRepository;
        this.summaryRepository = summaryRepository;
        this.reflectionRepository = reflectionRepository;
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.outcomeAttributionReads = outcomeAttributionReads;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public RetrievedMemory retrieve(UUID sessionId, FishingSessionState state, GuidanceTrigger trigger) {
        UUID userId = state == null || state.session() == null ? null : state.session().userId();
        if (userId == null) {
            return RetrievedMemory.empty();
        }
        Instant now = clock.instant();
        UserFishingPreferences explicit = explicitRepository.findById(userId)
                .map(DefaultMemoryRetrievalService::toContract)
                .orElse(null);
        List<InferredUserPreference> inferred = inferredRepository.findByUserId(userId).stream()
                .map(row -> toInferred(row, now))
                .filter(pref -> InferredPreferenceConfidence.meetsThreshold(
                        pref.confidence(),
                        properties.getMemory().getInferredConfidenceThreshold()
                ))
                .filter(pref -> !coveredByExplicit(pref.key(), explicit))
                .toList();
        List<String> memoryIds = new ArrayList<>();
        if (explicit != null) {
            memoryIds.add("pref:explicit:" + userId);
        }
        inferred.forEach(pref -> memoryIds.add("pref:inferred:" + pref.userId() + ":" + pref.key()));
        SessionSummaryEntity summary = summaryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (summary != null) {
            memoryIds.add("summary:" + summary.getFishingSessionId());
        }
        if (sessionId != null) {
            for (AgentReflectionEntity reflection : reflectionRepository.findByFishingSessionIdOrderByCreatedAtAsc(sessionId)) {
                memoryIds.add("reflection:" + reflection.getId());
            }
            for (FollowedOutcomeClip clip : outcomeAttributionReads.findFollowed(sessionId)) {
                memoryIds.add(clip.memoryRefId());
            }
            for (SessionEventEntity event : preferenceConflictEvents(sessionId)) {
                memoryIds.add("event:" + event.getId());
            }
        }
        for (SemanticMemoryEntity memory : semanticMemoryRepository.findTop8ByUserIdOrderByCreatedAtDesc(userId)) {
            memoryIds.add("semantic:" + memory.getId());
        }
        Set<String> unique = new LinkedHashSet<>(memoryIds);
        return new RetrievedMemory(explicit, inferred, List.copyOf(unique));
    }

    private List<SessionEventEntity> preferenceConflictEvents(UUID sessionId) {
        return sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
                sessionId,
                List.of(SessionEventType.ADVICE_REJECTED)
        );
    }

    static boolean coveredByExplicit(String key, UserFishingPreferences explicit) {
        if (explicit == null || key == null) {
            return false;
        }
        return switch (key) {
            case InferredPreferenceKeys.MAX_MOVE_METERS -> explicit.maxMoveMeters() != null;
            case InferredPreferenceKeys.AVOID_LONG_MOVE_IN_WIND -> explicit.avoidLongMoveInWind() != null;
            case InferredPreferenceKeys.WIND_CONSERVATISM -> explicit.windConservatism() != null;
            default -> false;
        };
    }

    private InferredUserPreference toInferred(InferredUserPreferenceEntity row, Instant now) {
        double confidence = InferredPreferenceConfidence.withRecency(
                row.getEvidenceCount(),
                row.getLastObservedAt(),
                now,
                properties.getMemory().getInferredRecencyHalfLifeDays()
        );
        return new InferredUserPreference(
                row.getSchemaVersion() == null ? GuidanceSchemaVersion.VALUE : row.getSchemaVersion(),
                row.getUserId(),
                row.getKey(),
                row.getValue(),
                row.getEvidenceCount(),
                confidence,
                row.getFirstObservedAt(),
                row.getLastObservedAt()
        );
    }

    static UserFishingPreferences toContract(UserFishingPreferencesEntity entity) {
        return new UserFishingPreferences(
                entity.getSchemaVersion() == null ? GuidanceSchemaVersion.VALUE : entity.getSchemaVersion(),
                entity.getUserId(),
                entity.getAvoidLongMoveInWind(),
                entity.getPreferredTechniques(),
                entity.getDislikedTechniques(),
                entity.getMaxMoveMeters() == null ? null : entity.getMaxMoveMeters().doubleValue(),
                entity.getWindConservatism() == null ? null : entity.getWindConservatism().doubleValue()
        );
    }
}
