package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LearningJobDispatcherTest {

    @Test
    void unregisteredJobTypesAreUnhandledAndRegisteredTypesRun() {
        AtomicInteger attributed = new AtomicInteger();
        LearningJobHandler attribute = handler(LearningJobType.ATTRIBUTE_OUTCOME, attributed);
        LearningJobHandlerRegistry registry = new LearningJobHandlerRegistry(List.of(attribute));
        GuidanceLearningJobDispatcher dispatcher = new GuidanceLearningJobDispatcher(registry);

        assertThat(registry.supported()).containsExactly(LearningJobType.ATTRIBUTE_OUTCOME);
        assertThat(dispatcher.dispatch(job(LearningJobType.ATTRIBUTE_OUTCOME))).isEqualTo(LearningDispatchResult.HANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.AGGREGATE_EMPIRICAL))).isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.PREFERENCE_UPDATE))).isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(dispatcher.dispatch(job(LearningJobType.SESSION_SUMMARY))).isEqualTo(LearningDispatchResult.UNHANDLED);
        assertThat(attributed.get()).isEqualTo(1);
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

    private static GuidanceLearningOutboxEntity job(LearningJobType type) {
        GuidanceLearningOutboxEntity entity = new GuidanceLearningOutboxEntity();
        entity.setJobType(type);
        return entity;
    }
}
