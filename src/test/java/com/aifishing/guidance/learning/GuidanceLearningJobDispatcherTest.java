package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.InMemoryAgentRuntimeControlStore;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceLearningJobDispatcherTest {

    @Test
    void registeredPreferenceAndSummaryHandlersRun() {
        AtomicInteger prefs = new AtomicInteger();
        AtomicInteger summaries = new AtomicInteger();
        GuidanceLearningJobDispatcher dispatcher = new GuidanceLearningJobDispatcher(
                new LearningJobHandlerRegistry(List.of(
                        handler(LearningJobType.PREFERENCE_UPDATE, prefs),
                        handler(LearningJobType.SESSION_SUMMARY, summaries)
                ))
        );

        assertThat(dispatcher.dispatch(job(LearningJobType.PREFERENCE_UPDATE)))
                .isEqualTo(LearningDispatchResult.HANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.SESSION_SUMMARY)))
                .isEqualTo(LearningDispatchResult.HANDLED);
        assertThat(prefs.get()).isEqualTo(1);
        assertThat(summaries.get()).isEqualTo(1);
    }

    @Test
    void missingHandlerIsUnhandledAndNotAckedByDispatcher() {
        GuidanceLearningJobDispatcher dispatcher = new GuidanceLearningJobDispatcher(
                new LearningJobHandlerRegistry(List.of())
        );

        assertThat(dispatcher.dispatch(job(LearningJobType.ATTRIBUTE_OUTCOME)))
                .isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.AGGREGATE_EMPIRICAL)))
                .isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.REFLECTION_EVAL)))
                .isEqualTo(LearningDispatchResult.UNHANDLED);
    }

    @Test
    void learningDisabledDefersMutationAndStillHandlesObservation() {
        AtomicInteger prefs = new AtomicInteger();
        AtomicInteger attributed = new AtomicInteger();
        InMemoryAgentRuntimeControlStore control = new InMemoryAgentRuntimeControlStore(new AgentRuntimeControl(
                true, "v1", null, false, false, java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        GuidanceLearningJobDispatcher dispatcher = new GuidanceLearningJobDispatcher(
                new LearningJobHandlerRegistry(List.of(
                        handler(LearningJobType.PREFERENCE_UPDATE, prefs),
                        handler(LearningJobType.ATTRIBUTE_OUTCOME, attributed)
                )),
                control
        );

        assertThat(dispatcher.dispatch(job(LearningJobType.PREFERENCE_UPDATE)))
                .isEqualTo(LearningDispatchResult.DEFERRED);
        assertThat(dispatcher.dispatch(job(LearningJobType.SESSION_SUMMARY)))
                .isEqualTo(LearningDispatchResult.DEFERRED);
        assertThat(dispatcher.dispatch(job(LearningJobType.AGGREGATE_EMPIRICAL)))
                .isEqualTo(LearningDispatchResult.DEFERRED);
        assertThat(dispatcher.dispatch(job(LearningJobType.REFLECTION_EVAL)))
                .isEqualTo(LearningDispatchResult.DEFERRED);
        assertThat(dispatcher.dispatch(job(LearningJobType.ATTRIBUTE_OUTCOME)))
                .isEqualTo(LearningDispatchResult.HANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.ONLINE_METRICS_ROLLUP)))
                .isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(prefs.get()).isZero();
        assertThat(attributed.get()).isEqualTo(1);
    }

    @Test
    void learningDisabledStillHandlesOnlineMetricsRollup() {
        AtomicInteger rollups = new AtomicInteger();
        InMemoryAgentRuntimeControlStore control = new InMemoryAgentRuntimeControlStore(new AgentRuntimeControl(
                true, "v1", null, false, false, java.time.Instant.parse("2026-09-18T14:00:00Z")
        ));
        GuidanceLearningJobDispatcher dispatcher = new GuidanceLearningJobDispatcher(
                new LearningJobHandlerRegistry(List.of(handler(LearningJobType.ONLINE_METRICS_ROLLUP, rollups))),
                control
        );

        assertThat(dispatcher.dispatch(job(LearningJobType.ONLINE_METRICS_ROLLUP)))
                .isEqualTo(LearningDispatchResult.HANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.AGGREGATE_EMPIRICAL)))
                .isEqualTo(LearningDispatchResult.DEFERRED);
        assertThat(rollups.get()).isEqualTo(1);
    }

    private static GuidanceLearningOutboxEntity job(LearningJobType type) {
        GuidanceLearningOutboxEntity entity = new GuidanceLearningOutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setJobType(type);
        return entity;
    }

    private static LearningJobHandler handler(LearningJobType type, AtomicInteger calls) {
        return new LearningJobHandler() {
            @Override
            public LearningJobType jobType() {
                return type;
            }

            @Override
            public void handle(GuidanceLearningOutboxEntity job) {
                calls.incrementAndGet();
            }
        };
    }
}
