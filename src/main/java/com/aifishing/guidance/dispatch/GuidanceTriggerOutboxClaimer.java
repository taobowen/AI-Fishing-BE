package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.attribution.AdviceLifecycleWriter;
import com.aifishing.guidance.horizon.GuidanceHorizonWriter;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.spi.FishingAgentFacade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Crash-safe in-process claimer. {@code afterCommit} kick plus short poll.
 * Stale {@code CLAIMED} rows are reclaimed. {@code USER_REQUEST} is never claimed.
 */
@Component
public class GuidanceTriggerOutboxClaimer {

    private static final Logger log = LoggerFactory.getLogger(GuidanceTriggerOutboxClaimer.class);
    private static final int MAX_BATCH = 32;

    private final GuidanceTriggerOutboxRepository outboxRepository;
    private final FishingAgentFacade facade;
    private final GuidanceHorizonWriter horizonWriter;
    private final AdviceLifecycleWriter adviceLifecycleWriter;
    private final GuidanceProperties properties;
    private final Clock clock;
    private final TaskExecutor executor;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public GuidanceTriggerOutboxClaimer(
            GuidanceTriggerOutboxRepository outboxRepository,
            FishingAgentFacade facade,
            GuidanceHorizonWriter horizonWriter,
            AdviceLifecycleWriter adviceLifecycleWriter,
            GuidanceProperties properties,
            Clock clock,
            @Qualifier("guidanceDispatchExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.facade = facade;
        this.horizonWriter = horizonWriter;
        this.adviceLifecycleWriter = adviceLifecycleWriter;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void kick() {
        if (!properties.isDispatchEnabled()) {
            return;
        }
        executor.execute(() -> {
            try {
                drain();
            } catch (RuntimeException ex) {
                log.warn("Guidance outbox drain failed: {}", ex.getMessage());
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
        Instant staleBefore = now.minusMillis(properties.getOutbox().getStaleClaimMs());
        UUID token = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                        UPDATE guidance_trigger_outbox
                        SET status = 'CLAIMED', claimed_at = :now, claim_token = :token
                        WHERE id = (
                            SELECT id FROM guidance_trigger_outbox
                            WHERE status = 'PENDING'
                               OR (status = 'CLAIMED' AND claimed_at < :staleBefore)
                            ORDER BY created_at
                            LIMIT 1
                            FOR UPDATE SKIP LOCKED
                        )
                        RETURNING id
                        """)
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
        GuidanceTriggerOutboxEntity row = outboxRepository.findById(outboxId).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getPrimaryTrigger() == GuidanceTrigger.USER_REQUEST) {
            markDone(row.getId());
            return;
        }
        DecisionSnapshot snapshot = DecisionSnapshot.from(row);
        try {
            var result = facade.run(row.getFishingSessionId(), row.getPrimaryTrigger(), null, row.getId());
            if (!GuidanceFallback.isKillSwitch(result)) {
                horizonWriter.writeAfterDelivered(row.getFishingSessionId(), result);
                adviceLifecycleWriter.writeCreated(row.getFishingSessionId(), result);
            }
        } catch (RuntimeException ex) {
            log.warn("Guidance outbox run failed for session {}: {}", row.getFishingSessionId(), ex.getMessage());
        }
        finalizeRow(row.getId(), snapshot);
    }

    private void finalizeRow(UUID outboxId, DecisionSnapshot snapshot) {
        transactionTemplate.executeWithoutResult(status -> {
            GuidanceTriggerOutboxEntity current = outboxRepository.findById(outboxId).orElse(null);
            if (current == null || current.getStatus() == GuidanceTriggerOutboxStatus.DONE) {
                return;
            }
            boolean changed = snapshot.changed(current);
            UUID sessionId = current.getFishingSessionId();
            TriggerRoutingDecision leftover = changed
                    ? new TriggerRoutingDecision(
                            current.getPrimaryTrigger(),
                            current.getRelatedTriggers(),
                            current.getReasonCodes()
                    )
                    : null;
            GuidanceTriggerOutboxSource leftoverSource = current.getSource();
            current.setStatus(GuidanceTriggerOutboxStatus.DONE);
            current.setClaimToken(null);
            outboxRepository.saveAndFlush(current);
            if (leftover != null && leftover.primary() != null && leftover.primary() != GuidanceTrigger.USER_REQUEST) {
                GuidanceTriggerOutboxEntity pending = new GuidanceTriggerOutboxEntity();
                pending.setFishingSessionId(sessionId);
                pending.setPrimaryTrigger(leftover.primary());
                pending.setRelatedTriggers(new ArrayList<>(leftover.related()));
                pending.setReasonCodes(new ArrayList<>(leftover.reasonCodes()));
                pending.setSource(leftoverSource == null ? GuidanceTriggerOutboxSource.EVENT : leftoverSource);
                pending.setStatus(GuidanceTriggerOutboxStatus.PENDING);
                pending.setCreatedAt(clock.instant());
                outboxRepository.save(pending);
            }
        });
    }

    private void markDone(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(outboxId).ifPresent(row -> {
            row.setStatus(GuidanceTriggerOutboxStatus.DONE);
            row.setClaimToken(null);
            outboxRepository.save(row);
        }));
    }

    private static UUID asUuid(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(raw));
    }

    private record DecisionSnapshot(
            GuidanceTrigger primary,
            List<GuidanceTrigger> related,
            List<String> reasonCodes
    ) {
        static DecisionSnapshot from(GuidanceTriggerOutboxEntity row) {
            return new DecisionSnapshot(
                    row.getPrimaryTrigger(),
                    List.copyOf(row.getRelatedTriggers() == null ? List.of() : row.getRelatedTriggers()),
                    List.copyOf(row.getReasonCodes() == null ? List.of() : row.getReasonCodes())
            );
        }

        boolean changed(GuidanceTriggerOutboxEntity row) {
            if (!Objects.equals(primary, row.getPrimaryTrigger())) {
                return true;
            }
            List<GuidanceTrigger> nowRelated = row.getRelatedTriggers() == null ? List.of() : row.getRelatedTriggers();
            List<String> nowReasons = row.getReasonCodes() == null ? List.of() : row.getReasonCodes();
            return !related.equals(nowRelated) || !reasonCodes.equals(nowReasons);
        }
    }
}
