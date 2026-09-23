package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxStatus;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.LearningQuarantineReason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local claimer with retry / DLQ / quarantine. Unknown job types are
 * {@code QUARANTINED} with {@code UNKNOWN_JOB_TYPE}, not left {@code PENDING}.
 * Phase 6 replaces only this transport, not the outbox row.
 */
@Component
public class GuidanceLearningOutboxClaimer {

    private static final Logger log = LoggerFactory.getLogger(GuidanceLearningOutboxClaimer.class);
    private static final int MAX_BATCH = 32;

    private final GuidanceLearningOutboxRepository outboxRepository;
    private final GuidanceLearningJobDispatcher dispatcher;
    private final LearningJobHandlerRegistry handlerRegistry;
    private final AgentRuntimeControlStore runtimeControlStore;
    private final GuidanceProperties properties;
    private final Clock clock;
    private final TaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    public GuidanceLearningOutboxClaimer(
            GuidanceLearningOutboxRepository outboxRepository,
            GuidanceLearningJobDispatcher dispatcher,
            LearningJobHandlerRegistry handlerRegistry,
            AgentRuntimeControlStore runtimeControlStore,
            GuidanceProperties properties,
            Clock clock,
            @Qualifier("guidanceLearningExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.handlerRegistry = handlerRegistry;
        this.runtimeControlStore = runtimeControlStore;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
    }

    public void kick() {
        if (!properties.getLearningOutbox().isEnabled()) {
            return;
        }
        executor.execute(() -> {
            try {
                drain();
            } catch (RuntimeException ex) {
                log.warn("Learning outbox drain failed: {}", ex.getMessage());
            }
        });
    }

    public int drain() {
        int processed = 0;
        while (processed < MAX_BATCH) {
            UUID claimedId = transactionTemplate.execute(status -> claimNext().orElse(null));
            if (claimedId == null) {
                break;
            }
            process(claimedId);
            processed++;
        }
        return processed;
    }

    public Optional<UUID> claimNext() {
        Instant now = clock.instant();
        Instant staleBefore = now.minusMillis(properties.getLearningOutbox().getStaleClaimMs());
        UUID token = UUID.randomUUID();
        String mutationGate = learningEnabled()
                ? ""
                : "AND job_type IN ('ATTRIBUTE_OUTCOME', 'ONLINE_METRICS_ROLLUP')";
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                        UPDATE guidance_learning_outbox
                        SET status = 'CLAIMED', claimed_at = :now, claim_token = :token
                        WHERE id = (
                            SELECT id FROM guidance_learning_outbox
                            WHERE (
                                    (status = 'PENDING' AND available_at <= :now)
                                 OR (status = 'CLAIMED' AND claimed_at < :staleBefore)
                              )
                            %s
                            ORDER BY created_at
                            LIMIT 1
                            FOR UPDATE SKIP LOCKED
                        )
                        RETURNING id
                        """.formatted(mutationGate))
                .setParameter("now", now)
                .setParameter("token", token)
                .setParameter("staleBefore", staleBefore)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(asUuid(rows.getFirst()));
    }

    private void process(UUID outboxId) {
        String jobTypeName = jobTypeName(outboxId);
        if (jobTypeName == null) {
            return;
        }
        if (registeredJobType(jobTypeName).isEmpty()) {
            quarantineUnknown(outboxId, jobTypeName);
            return;
        }
        GuidanceLearningOutboxEntity row = outboxRepository.findById(outboxId).orElse(null);
        if (row == null) {
            return;
        }
        try {
            LearningDispatchResult result = dispatcher.dispatch(row);
            if (result == LearningDispatchResult.DEFERRED) {
                unclaimToPending(row.getId());
                return;
            }
            if (result == LearningDispatchResult.UNHANDLED) {
                quarantineUnknown(row.getId(), row.getJobType().name());
                return;
            }
            markDone(row.getId());
            meterRegistry.counter("guidance.learning.outbox", "result", "done", "jobType", row.getJobType().name())
                    .increment();
        } catch (RuntimeException ex) {
            log.warn("Learning job {} {} failed: {}", row.getJobType(), row.getId(), ex.getMessage());
            markFailed(row.getId(), ex.getMessage());
        }
    }

    private void unclaimToPending(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(outboxId).ifPresent(row -> {
            row.setStatus(GuidanceLearningOutboxStatus.PENDING);
            row.setClaimToken(null);
            row.setClaimedAt(null);
            outboxRepository.save(row);
        }));
    }

    private boolean learningEnabled() {
        if (runtimeControlStore == null) {
            return true;
        }
        return runtimeControlStore.load().learningEnabled();
    }

    private String jobTypeName(UUID outboxId) {
        return transactionTemplate.execute(status -> {
            @SuppressWarnings("unchecked")
            List<Object> rows = entityManager.createNativeQuery(
                            "SELECT job_type FROM guidance_learning_outbox WHERE id = :id"
                    )
                    .setParameter("id", outboxId)
                    .getResultList();
            if (rows.isEmpty() || rows.getFirst() == null) {
                return null;
            }
            return String.valueOf(rows.getFirst());
        });
    }

    private Optional<LearningJobType> registeredJobType(String jobTypeName) {
        return registeredJobType(jobTypeName, handlerRegistry);
    }

    static Optional<LearningJobType> registeredJobType(String jobTypeName, LearningJobHandlerRegistry registry) {
        if (jobTypeName == null || jobTypeName.isBlank()) {
            return Optional.empty();
        }
        LearningJobType type;
        try {
            type = LearningJobType.valueOf(jobTypeName);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return registry.find(type).isPresent() ? Optional.of(type) : Optional.empty();
    }

    private void quarantineUnknown(UUID outboxId, String jobTypeName) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            UPDATE guidance_learning_outbox
                            SET status = 'QUARANTINED',
                                quarantine_reason = :reason,
                                claim_token = NULL,
                                last_error = :error
                            WHERE id = :id
                            """)
                    .setParameter("reason", LearningQuarantineReason.UNKNOWN_JOB_TYPE.name())
                    .setParameter("error", truncate("Unknown or unregistered LearningJobType: " + jobTypeName))
                    .setParameter("id", outboxId)
                    .executeUpdate();
        });
        log.warn("Learning job {} {} quarantined: UNKNOWN_JOB_TYPE", jobTypeName, outboxId);
        meterRegistry.counter(
                "guidance.learning.outbox",
                "result", "quarantined",
                "jobType", jobTypeName == null || jobTypeName.isBlank() ? "unknown" : jobTypeName
        ).increment();
    }

    private void markDone(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(outboxId).ifPresent(row -> {
            row.setStatus(GuidanceLearningOutboxStatus.DONE);
            row.setClaimToken(null);
            row.setLastError(null);
            outboxRepository.save(row);
        }));
    }

    private void markFailed(UUID outboxId, String error) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(outboxId).ifPresent(row -> {
            int attempts = row.getAttemptCount() + 1;
            row.setAttemptCount(attempts);
            row.setLastError(error == null ? "learning job failed" : truncate(error));
            row.setClaimToken(null);
            if (attempts >= row.getMaxAttempts()) {
                row.setStatus(GuidanceLearningOutboxStatus.DLQ);
                meterRegistry.counter(
                        "guidance.learning.outbox",
                        "result", "dlq",
                        "jobType", row.getJobType().name()
                ).increment();
            } else {
                row.setStatus(GuidanceLearningOutboxStatus.PENDING);
                row.setAvailableAt(clock.instant().plus(backoff(attempts)));
                meterRegistry.counter(
                        "guidance.learning.outbox",
                        "result", "retry",
                        "jobType", row.getJobType().name()
                ).increment();
            }
            outboxRepository.save(row);
        }));
    }

    private static Duration backoff(int attempts) {
        long seconds = Math.min(300, (long) Math.pow(2, Math.max(0, attempts - 1)));
        return Duration.ofSeconds(seconds);
    }

    private static String truncate(String error) {
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    private static UUID asUuid(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(raw));
    }
}
