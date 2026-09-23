package com.aifishing.guidance.learning;

import com.aifishing.guidance.attribution.OutcomeAttributor;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttributeOutcomeJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T16:20:00Z");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private OutcomeAttributor outcomeAttributor;
    @Mock
    private GuidanceLearningOutboxService learningOutboxService;

    private AttributeOutcomeJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AttributeOutcomeJobHandler(
                outcomeAttributor,
                learningOutboxService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void attributesThenEnqueuesReflectionAndHourAndDayRollups() {
        GuidanceLearningOutboxEntity job = new GuidanceLearningOutboxEntity();
        job.setId(JOB);
        job.setFishingSessionId(SESSION);
        job.setJobType(LearningJobType.ATTRIBUTE_OUTCOME);

        handler.handle(job);

        verify(outcomeAttributor).attribute(SESSION);
        ArgumentCaptor<LearningJobType> types = ArgumentCaptor.forClass(LearningJobType.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(learningOutboxService, times(3)).enqueue(
                eq(SESSION),
                types.capture(),
                keys.capture(),
                any(Map.class)
        );
        assertThat(types.getAllValues()).containsExactly(
                LearningJobType.REFLECTION_EVAL,
                LearningJobType.ONLINE_METRICS_ROLLUP,
                LearningJobType.ONLINE_METRICS_ROLLUP
        );
        assertThat(keys.getAllValues()).contains(
                "reflection-eval:" + SESSION + ":" + JOB,
                "online-metrics-rollup:HOUR:2026-09-16T16:00:00Z:" + JOB,
                "online-metrics-rollup:DAY:2026-09-16T00:00:00Z:" + JOB
        );
        List<LearningJobType> rollups = types.getAllValues().stream()
                .filter(type -> type == LearningJobType.ONLINE_METRICS_ROLLUP)
                .toList();
        assertThat(rollups).hasSize(2);
    }
}
