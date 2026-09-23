package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceLearningOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T20:15:00Z");

    @Mock
    private GuidanceLearningOutboxRepository outboxRepository;
    @Mock
    private GuidanceLearningOutboxClaimer claimer;

    private GuidanceLearningOutboxService service;

    @BeforeEach
    void setUp() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.getLearningOutbox().setEnabled(false);
        service = new GuidanceLearningOutboxService(
                outboxRepository,
                claimer,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void enqueueReturnsExistingRowForSameIdempotencyKey() {
        GuidanceLearningOutboxEntity existing = new GuidanceLearningOutboxEntity();
        existing.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        existing.setIdempotencyKey("same-key");
        existing.setStatus(GuidanceLearningOutboxStatus.PENDING);
        when(outboxRepository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        GuidanceLearningOutboxEntity first = service.enqueue(
                null,
                LearningJobType.SESSION_SUMMARY,
                "same-key",
                Map.of("n", 1)
        );
        GuidanceLearningOutboxEntity second = service.enqueue(
                null,
                LearningJobType.SESSION_SUMMARY,
                "same-key",
                Map.of("n", 2)
        );

        assertThat(first.getId()).isEqualTo(existing.getId());
        assertThat(second.getId()).isEqualTo(existing.getId());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void enqueueCreatesPendingRowOnce() {
        when(outboxRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(outboxRepository.save(any())).thenAnswer(invocation -> {
            GuidanceLearningOutboxEntity row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
            }
            return row;
        });

        GuidanceLearningOutboxEntity created = service.enqueue(
                null,
                LearningJobType.SESSION_SUMMARY,
                "new-key",
                Map.of()
        );

        ArgumentCaptor<GuidanceLearningOutboxEntity> captor = ArgumentCaptor.forClass(GuidanceLearningOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GuidanceLearningOutboxStatus.PENDING);
        assertThat(captor.getValue().getJobType()).isEqualTo(LearningJobType.SESSION_SUMMARY);
        assertThat(created.getIdempotencyKey()).isEqualTo("new-key");
    }
}
