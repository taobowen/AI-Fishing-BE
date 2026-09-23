package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.InferredUserPreference;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.UserFishingPreferences;
import com.aifishing.guidance.persistence.AgentReflectionEntity;
import com.aifishing.guidance.persistence.AgentReflectionRepository;
import com.aifishing.guidance.persistence.InferredUserPreferenceEntity;
import com.aifishing.guidance.persistence.InferredUserPreferenceRepository;
import com.aifishing.guidance.persistence.SemanticMemoryRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.SessionSummaryEntity;
import com.aifishing.guidance.persistence.SessionSummaryRepository;
import com.aifishing.guidance.persistence.UserFishingPreferencesEntity;
import com.aifishing.guidance.persistence.UserFishingPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultMemoryRetrievalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");

    @Mock
    private UserFishingPreferencesRepository explicitRepository;
    @Mock
    private InferredUserPreferenceRepository inferredRepository;
    @Mock
    private SessionSummaryRepository summaryRepository;
    @Mock
    private AgentReflectionRepository reflectionRepository;
    @Mock
    private SemanticMemoryRepository semanticMemoryRepository;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private OutcomeAttributionReads outcomeAttributionReads;

    private DefaultMemoryRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new DefaultMemoryRetrievalService(
                explicitRepository,
                inferredRepository,
                summaryRepository,
                reflectionRepository,
                semanticMemoryRepository,
                sessionEventRepository,
                outcomeAttributionReads,
                new GuidanceProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void stubEmptyLookups() {
        when(summaryRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(reflectionRepository.findByFishingSessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(List.of());
        when(semanticMemoryRepository.findTop8ByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of());
        when(outcomeAttributionReads.findFollowed(SESSION_ID)).thenReturn(List.of());
    }

    @Test
    void singleTooFarInferredIsNotReturnedAsHardConstraint() {
        stubEmptyLookups();
        when(explicitRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(inferredRepository.findByUserId(USER_ID)).thenReturn(List.of(inferred(
                InferredPreferenceKeys.MAX_MOVE_METERS, "constrained", 1, NOW
        )));

        RetrievedMemory memory = service.retrieve(SESSION_ID, GuidancePhase2Fixtures.safeState(), GuidanceTrigger.NO_BITE_THRESHOLD);

        assertThat(memory.userPreferences()).isNull();
        assertThat(memory.inferredPreferences()).isEmpty();
        assertThat(memory.retrievedMemoryIds()).doesNotContain("pref:inferred:" + USER_ID + ":maxMoveMeters");
    }

    @Test
    void explicitOverridesInferredOnTheSameKey() {
        stubEmptyLookups();
        UserFishingPreferencesEntity explicit = new UserFishingPreferencesEntity();
        explicit.setUserId(USER_ID);
        explicit.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        explicit.setMaxMoveMeters(BigDecimal.valueOf(400));
        when(explicitRepository.findById(USER_ID)).thenReturn(Optional.of(explicit));
        when(inferredRepository.findByUserId(USER_ID)).thenReturn(List.of(inferred(
                InferredPreferenceKeys.MAX_MOVE_METERS, "constrained", 5, NOW
        )));

        RetrievedMemory memory = service.retrieve(SESSION_ID, GuidancePhase2Fixtures.safeState(), GuidanceTrigger.USER_REQUEST);

        assertThat(memory.userPreferences()).isInstanceOf(UserFishingPreferences.class);
        assertThat(memory.userPreferences().maxMoveMeters()).isEqualTo(400.0);
        assertThat(memory.inferredPreferences()).isEmpty();
        assertThat(memory.retrievedMemoryIds()).contains("pref:explicit:" + USER_ID);
        assertThat(memory.retrievedMemoryIds()).doesNotContain("pref:inferred:" + USER_ID + ":maxMoveMeters");
    }

    @Test
    void overThresholdInferredAndSummaryAndFollowedFishOnAreReferenced() {
        when(explicitRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(inferredRepository.findByUserId(USER_ID)).thenReturn(List.of(inferred(
                InferredPreferenceKeys.STAY_PREFERRED, "true", 3, NOW
        )));
        SessionSummaryEntity summary = new SessionSummaryEntity();
        UUID priorSession = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        summary.setFishingSessionId(priorSession);
        when(summaryRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(summary));
        AgentReflectionEntity reflection = new AgentReflectionEntity();
        reflection.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        when(reflectionRepository.findByFishingSessionIdOrderByCreatedAtAsc(SESSION_ID))
                .thenReturn(List.of(reflection));
        UUID attributionId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(outcomeAttributionReads.findFollowed(SESSION_ID)).thenReturn(List.of(
                new FollowedOutcomeClip(attributionId, "LURE", "FISH_ON", true)
        ));
        SessionEventEntity rejected = new SessionEventEntity();
        rejected.setId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        when(sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of(rejected));
        when(semanticMemoryRepository.findTop8ByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        RetrievedMemory memory = service.retrieve(SESSION_ID, GuidancePhase2Fixtures.safeState(), GuidanceTrigger.NO_BITE_THRESHOLD);

        assertThat(memory.inferredPreferences())
                .extracting(InferredUserPreference::key)
                .containsExactly(InferredPreferenceKeys.STAY_PREFERRED);
        assertThat(memory.retrievedMemoryIds()).contains(
                "pref:inferred:" + USER_ID + ":stayPreferred",
                "summary:" + priorSession,
                "reflection:" + reflection.getId(),
                "attribution:" + attributionId,
                "event:" + rejected.getId()
        );
    }

    private static InferredUserPreferenceEntity inferred(String key, String value, int evidence, Instant lastObservedAt) {
        InferredUserPreferenceEntity entity = new InferredUserPreferenceEntity();
        entity.setId(UUID.randomUUID());
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setUserId(USER_ID);
        entity.setKey(key);
        entity.setValue(value);
        entity.setEvidenceCount(evidence);
        entity.setFirstObservedAt(lastObservedAt);
        entity.setLastObservedAt(lastObservedAt);
        return entity;
    }
}
