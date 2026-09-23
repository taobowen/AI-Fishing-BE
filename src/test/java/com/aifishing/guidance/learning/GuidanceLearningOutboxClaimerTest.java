package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceLearningOutboxClaimerTest {

    @Test
    void unknownAndUnregisteredTypesAreNotRegistered() {
        LearningJobHandlerRegistry empty = new LearningJobHandlerRegistry(List.of());
        LearningJobHandlerRegistry attributeOnly = new LearningJobHandlerRegistry(List.of(handler()));

        assertThat(GuidanceLearningOutboxClaimer.registeredJobType("NOT_A_REAL_JOB", empty)).isEmpty();
        assertThat(GuidanceLearningOutboxClaimer.registeredJobType("ONLINE_METRICS_ROLLUP", empty)).isEmpty();
        assertThat(GuidanceLearningOutboxClaimer.registeredJobType(LearningJobType.ATTRIBUTE_OUTCOME.name(), empty))
                .isEmpty();
        assertThat(GuidanceLearningOutboxClaimer.registeredJobType(
                LearningJobType.ATTRIBUTE_OUTCOME.name(),
                attributeOnly
        )).contains(LearningJobType.ATTRIBUTE_OUTCOME);
        assertThat(GuidanceLearningOutboxClaimer.registeredJobType(
                LearningJobType.REFLECTION_EVAL.name(),
                attributeOnly
        )).isEmpty();
        assertThat(GuidanceLearningOutboxClaimer.registeredJobType(null, attributeOnly)).isEmpty();
    }

    private static LearningJobHandler handler() {
        return new LearningJobHandler() {
            @Override
            public LearningJobType jobType() {
                return LearningJobType.ATTRIBUTE_OUTCOME;
            }

            @Override
            public void handle(GuidanceLearningOutboxEntity job) {
            }
        };
    }
}
