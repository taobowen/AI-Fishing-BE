package com.aifishing.guidance.learning;

import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.SemanticMemoryKind;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceRepository;
import com.aifishing.guidance.persistence.SemanticMemoryEntity;
import com.aifishing.guidance.persistence.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceUpdateJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private InferredUserPreferenceRepository inferredRepository;
    @Mock
    private SemanticMemoryRepository semanticMemoryRepository;

    private PreferenceUpdateJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PreferenceUpdateJobHandler(
                sessionRepository,
                inferredRepository,
                semanticMemoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void oneTooFarIncrementsEvidenceButIsNotAHardConstraint() {
        when(inferredRepository.findByUserIdAndKey(USER, InferredPreferenceKeys.MAX_MOVE_METERS))
                .thenReturn(Optional.empty());

        handler.handle(job(GuidanceRejectReason.TOO_FAR, null));

        ArgumentCaptor<InferredUserPreferenceEntity> captor =
                ArgumentCaptor.forClass(InferredUserPreferenceEntity.class);
        verify(inferredRepository).save(captor.capture());
        InferredUserPreferenceEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getKey()).isEqualTo(InferredPreferenceKeys.MAX_MOVE_METERS);
        assertThat(saved.getEvidenceCount()).isEqualTo(1);
        assertThat(saved.getConfidence().doubleValue()).isCloseTo(0.283, within(0.001));
        assertThat(InferredPreferenceConfidence.meetsThreshold(saved.getConfidence().doubleValue(), 0.5)).isFalse();
        verify(semanticMemoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectNeverWritesExplicitPreferencesTable() {
        when(inferredRepository.findByUserIdAndKey(USER, InferredPreferenceKeys.STAY_PREFERRED))
                .thenReturn(Optional.empty());

        handler.handle(job(GuidanceRejectReason.WANT_TO_STAY, "stay here"));

        verify(inferredRepository).save(org.mockito.ArgumentMatchers.any());
        verify(sessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(semanticMemoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unmappedRejectWithNoteWritesSemanticMemoryOnly() {
        handler.handle(job(GuidanceRejectReason.OTHER, "I just don't like that spot"));

        ArgumentCaptor<SemanticMemoryEntity> captor = ArgumentCaptor.forClass(SemanticMemoryEntity.class);
        verify(semanticMemoryRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(SemanticMemoryKind.FREE_TEXT_FEEDBACK);
        assertThat(captor.getValue().getText()).contains("don't like");
        assertThat(captor.getValue().getEmbeddingRef()).isNull();
        verify(inferredRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedRejectRaisesEvidenceAndConfidence() {
        InferredUserPreferenceEntity existing = new InferredUserPreferenceEntity();
        existing.setUserId(USER);
        existing.setKey(InferredPreferenceKeys.MAX_MOVE_METERS);
        existing.setValue("constrained");
        existing.setEvidenceCount(2);
        existing.setFirstObservedAt(NOW.minusSeconds(3600));
        when(inferredRepository.findByUserIdAndKey(USER, InferredPreferenceKeys.MAX_MOVE_METERS))
                .thenReturn(Optional.of(existing));

        handler.handle(job(GuidanceRejectReason.TOO_FAR, null));

        ArgumentCaptor<InferredUserPreferenceEntity> captor =
                ArgumentCaptor.forClass(InferredUserPreferenceEntity.class);
        verify(inferredRepository).save(captor.capture());
        assertThat(captor.getValue().getEvidenceCount()).isEqualTo(3);
        assertThat(captor.getValue().getConfidence().doubleValue()).isCloseTo(0.632, within(0.001));
        assertThat(InferredPreferenceConfidence.meetsThreshold(
                captor.getValue().getConfidence().doubleValue(), 0.5)).isTrue();
    }

    private GuidanceLearningOutboxEntity job(GuidanceRejectReason reason, String note) {
        GuidanceLearningOutboxEntity entity = new GuidanceLearningOutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setFishingSessionId(SESSION);
        entity.setJobType(LearningJobType.PREFERENCE_UPDATE);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("userId", USER.toString());
        payload.put("rejectReason", reason.name());
        if (note != null) {
            payload.put("note", note);
        }
        entity.setPayload(payload);
        return entity;
    }
}
