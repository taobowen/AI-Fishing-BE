package com.aifishing.guidance.learning;

import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.SemanticMemoryKind;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceRepository;
import com.aifishing.guidance.persistence.SemanticMemoryEntity;
import com.aifishing.guidance.persistence.SemanticMemoryRepository;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Adds inferred preference evidence from reject reasons. Never writes
 * {@code user_fishing_preferences}.
 */
@Component
public class PreferenceUpdateJobHandler implements LearningJobHandler {

    private final FishingSessionRepository sessionRepository;
    private final InferredUserPreferenceRepository inferredRepository;
    private final SemanticMemoryRepository semanticMemoryRepository;
    private final Clock clock;

    public PreferenceUpdateJobHandler(
            FishingSessionRepository sessionRepository,
            InferredUserPreferenceRepository inferredRepository,
            SemanticMemoryRepository semanticMemoryRepository,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.inferredRepository = inferredRepository;
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.clock = clock;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.PREFERENCE_UPDATE;
    }

    @Override
    @Transactional
    public void handle(GuidanceLearningOutboxEntity job) {
        Map<String, Object> payload = job.getPayload() == null ? Map.of() : job.getPayload();
        UUID userId = resolveUserId(job.getFishingSessionId(), payload);
        if (userId == null) {
            throw new IllegalStateException("PREFERENCE_UPDATE requires userId or fishingSessionId");
        }
        GuidanceRejectReason reason = parseReason(payload.get("rejectReason"));
        if (reason == null) {
            reason = parseReason(payload.get("reason"));
        }
        String note = text(payload.get("note"));
        Instant now = clock.instant();

        List<RejectReasonPreferenceMap.MappedPreference> mappings = RejectReasonPreferenceMap.mappings(reason, payload);
        for (RejectReasonPreferenceMap.MappedPreference mapping : mappings) {
            upsertInferred(userId, mapping.key(), mapping.value(), now);
        }
        if (RejectReasonPreferenceMap.writesSemanticNote(reason) && note != null && !note.isBlank()) {
            SemanticMemoryEntity memory = new SemanticMemoryEntity();
            memory.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            memory.setUserId(userId);
            memory.setKind(SemanticMemoryKind.FREE_TEXT_FEEDBACK);
            memory.setText(clip(note, 2000));
            memory.setEmbeddingRef(null);
            memory.setCreatedAt(now);
            semanticMemoryRepository.save(memory);
        }
    }

    private void upsertInferred(UUID userId, String key, String value, Instant now) {
        InferredUserPreferenceEntity row = inferredRepository.findByUserIdAndKey(userId, key).orElse(null);
        if (row == null) {
            row = new InferredUserPreferenceEntity();
            row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            row.setUserId(userId);
            row.setKey(key);
            row.setValue(clip(value, 512));
            row.setEvidenceCount(1);
            row.setFirstObservedAt(now);
            row.setCreatedAt(now);
        } else {
            row.setEvidenceCount(row.getEvidenceCount() + 1);
            row.setValue(clip(value, 512));
        }
        row.setLastObservedAt(now);
        row.setConfidence(BigDecimal.valueOf(InferredPreferenceConfidence.fromEvidence(row.getEvidenceCount()))
                .setScale(3, RoundingMode.HALF_UP));
        inferredRepository.save(row);
    }

    private UUID resolveUserId(UUID sessionId, Map<String, Object> payload) {
        UUID fromPayload = parseUuid(payload.get("userId"));
        if (fromPayload != null) {
            return fromPayload;
        }
        if (sessionId == null) {
            return null;
        }
        return sessionRepository.findById(sessionId).map(FishingSession::getUserId).orElse(null);
    }

    private static GuidanceRejectReason parseReason(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return GuidanceRejectReason.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
