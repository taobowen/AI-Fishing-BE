package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Enqueue learning jobs in the same transaction as domain writes. Never write
 * aggregation jobs into {@code guidance_trigger_outbox}.
 */
@Service
public class GuidanceLearningOutboxService {

    private static final Logger log = LoggerFactory.getLogger(GuidanceLearningOutboxService.class);

    private final GuidanceLearningOutboxRepository outboxRepository;
    private final GuidanceLearningOutboxClaimer claimer;
    private final GuidanceProperties properties;
    private final Clock clock;

    public GuidanceLearningOutboxService(
            GuidanceLearningOutboxRepository outboxRepository,
            @Lazy GuidanceLearningOutboxClaimer claimer,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.claimer = claimer;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public GuidanceLearningOutboxEntity enqueue(
            UUID fishingSessionId,
            LearningJobType jobType,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        return enqueue(fishingSessionId, jobType, idempotencyKey, payload, clock.instant());
    }

    @Transactional
    public GuidanceLearningOutboxEntity enqueue(
            UUID fishingSessionId,
            LearningJobType jobType,
            String idempotencyKey,
            Map<String, Object> payload,
            Instant availableAt
    ) {
        Objects.requireNonNull(jobType, "jobType");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        GuidanceLearningOutboxEntity existing = outboxRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        GuidanceLearningOutboxEntity created = new GuidanceLearningOutboxEntity();
        created.setFishingSessionId(fishingSessionId);
        created.setJobType(jobType);
        created.setIdempotencyKey(idempotencyKey);
        created.setPayload(payload == null ? Map.of() : payload);
        created.setStatus(GuidanceLearningOutboxStatus.PENDING);
        created.setAttemptCount(0);
        created.setMaxAttempts(properties.getLearningOutbox().getMaxAttempts());
        created.setAvailableAt(availableAt == null ? clock.instant() : availableAt);
        created.setCreatedAt(clock.instant());
        GuidanceLearningOutboxEntity saved = outboxRepository.save(created);
        kickAfterCommit();
        return saved;
    }

    private void kickAfterCommit() {
        if (!properties.getLearningOutbox().isEnabled()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            claimer.kick();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    claimer.kick();
                } catch (RuntimeException ex) {
                    log.warn("Learning outbox afterCommit kick failed: {}", ex.getMessage());
                }
            }
        });
    }
}
