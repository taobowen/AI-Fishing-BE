package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One open PENDING/CLAIMED row per fishing session. Merges related triggers and
 * reason codes. Never enqueues {@code USER_REQUEST}.
 */
@Service
public class GuidanceTriggerOutboxService {

    private static final Logger log = LoggerFactory.getLogger(GuidanceTriggerOutboxService.class);
    private static final EnumSet<GuidanceTriggerOutboxStatus> OPEN =
            EnumSet.of(GuidanceTriggerOutboxStatus.PENDING, GuidanceTriggerOutboxStatus.CLAIMED);

    private final GuidanceTriggerOutboxRepository outboxRepository;
    private final GuidanceTriggerOutboxClaimer claimer;
    private final GuidanceProperties properties;
    private final Clock clock;

    public GuidanceTriggerOutboxService(
            GuidanceTriggerOutboxRepository outboxRepository,
            @Lazy GuidanceTriggerOutboxClaimer claimer,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.claimer = claimer;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<GuidanceTriggerOutboxEntity> upsert(
            UUID fishingSessionId,
            TriggerRoutingDecision decision,
            GuidanceTriggerOutboxSource source
    ) {
        Objects.requireNonNull(fishingSessionId, "fishingSessionId");
        if (decision == null || decision.primary() == null || decision.primary() == GuidanceTrigger.USER_REQUEST) {
            return Optional.empty();
        }
        GuidanceTriggerOutboxSource resolvedSource =
                source == null ? GuidanceTriggerOutboxSource.EVENT : source;
        GuidanceTriggerOutboxEntity open = outboxRepository
                .findFirstByFishingSessionIdAndStatusIn(fishingSessionId, OPEN)
                .orElse(null);
        if (open == null) {
            GuidanceTriggerOutboxEntity created = new GuidanceTriggerOutboxEntity();
            created.setFishingSessionId(fishingSessionId);
            created.setPrimaryTrigger(decision.primary());
            created.setRelatedTriggers(new ArrayList<>(decision.related()));
            created.setReasonCodes(new ArrayList<>(decision.reasonCodes()));
            created.setSource(resolvedSource);
            created.setStatus(GuidanceTriggerOutboxStatus.PENDING);
            created.setCreatedAt(clock.instant());
            GuidanceTriggerOutboxEntity saved = outboxRepository.save(created);
            kickAfterCommit();
            return Optional.of(saved);
        }
        mergeInto(open, decision, resolvedSource);
        GuidanceTriggerOutboxEntity saved = outboxRepository.save(open);
        kickAfterCommit();
        return Optional.of(saved);
    }

    public static void mergeInto(
            GuidanceTriggerOutboxEntity open,
            TriggerRoutingDecision decision,
            GuidanceTriggerOutboxSource incomingSource
    ) {
        GuidanceTrigger primary = TriggerPriority.higher(open.getPrimaryTrigger(), decision.primary());
        List<GuidanceTrigger> related = TriggerPriority.relatedWithoutPrimary(
                primary,
                TriggerPriority.allTriggers(open.getPrimaryTrigger(), open.getRelatedTriggers()),
                TriggerPriority.allTriggers(decision.primary(), decision.related())
        );
        open.setPrimaryTrigger(primary);
        open.setRelatedTriggers(new ArrayList<>(related));
        open.setReasonCodes(new ArrayList<>(TriggerPriority.mergeReasons(open.getReasonCodes(), decision.reasonCodes())));
        if (open.getSource() != GuidanceTriggerOutboxSource.EVENT && incomingSource == GuidanceTriggerOutboxSource.EVENT) {
            open.setSource(GuidanceTriggerOutboxSource.EVENT);
        }
    }

    private void kickAfterCommit() {
        if (!properties.isDispatchEnabled()) {
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
                    log.warn("Guidance outbox afterCommit kick failed: {}", ex.getMessage());
                }
            }
        });
    }
}
