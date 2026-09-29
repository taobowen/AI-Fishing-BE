package com.aifishing.planning.intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Source rows commit first. Spatial resolution runs after commit and never fails the save.
 */
@Component
public class IntentResolutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntentResolutionScheduler.class);

    private final IntentSpatialResolutionService resolutionService;

    public IntentResolutionScheduler(IntentSpatialResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    public void afterTemplateTargetsCommitted(List<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(targetIds);
        dispatch(() -> resolutionService.resolveTemplateTargets(ids));
    }

    public void afterRequiredPointsCommitted(List<UUID> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(pointIds);
        dispatch(() -> resolutionService.resolveRequiredPoints(ids));
    }

    private void dispatch(Runnable job) {
        Runnable safe = () -> {
            try {
                job.run();
            } catch (RuntimeException ex) {
                log.warn("Intent spatial resolution failed: {}", ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(safe);
                }
            });
            return;
        }
        CompletableFuture.runAsync(safe);
    }
}
