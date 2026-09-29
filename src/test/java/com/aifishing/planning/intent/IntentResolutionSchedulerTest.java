package com.aifishing.planning.intent;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IntentResolutionSchedulerTest {

    @Test
    void resolutionStartsOnlyAfterCommit() throws Exception {
        IntentSpatialResolutionService service = mock(IntentSpatialResolutionService.class);
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            return null;
        }).when(service).resolveTemplateTargets(anyList());
        IntentResolutionScheduler scheduler = new IntentResolutionScheduler(service);
        UUID id = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try {
            scheduler.afterTemplateTargetsCommitted(List.of(id));
            verify(service, never()).resolveTemplateTargets(anyList());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void matcherFailureDoesNotEscapeTheSaveThread() throws Exception {
        IntentSpatialResolutionService service = mock(IntentSpatialResolutionService.class);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean escaped = new AtomicBoolean(false);
        doAnswer(invocation -> {
            try {
                throw new IllegalStateException("snapshot missing");
            } finally {
                finished.countDown();
            }
        }).when(service).resolveRequiredPoints(anyList());
        IntentResolutionScheduler scheduler = new IntentResolutionScheduler(service);
        TransactionSynchronizationManager.initSynchronization();
        try {
            scheduler.afterRequiredPointsCommitted(List.of(UUID.randomUUID()));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                try {
                    synchronization.afterCommit();
                } catch (RuntimeException ex) {
                    escaped.set(true);
                }
            }
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(escaped).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
