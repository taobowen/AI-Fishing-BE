package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import com.aifishing.guidance.metrics.OnlineOutcomeMetricsService;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnlineMetricsRollupJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T16:20:00Z");

    @Mock
    private OnlineOutcomeMetricsService metricsService;

    @Test
    void rollsUpPayloadWindowAndDefaultsMissingBoundsToCurrentHour() {
        OnlineMetricsRollupJobHandler handler = new OnlineMetricsRollupJobHandler(
                metricsService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        assertThat(handler.jobType()).isEqualTo(LearningJobType.ONLINE_METRICS_ROLLUP);

        GuidanceLearningOutboxEntity job = new GuidanceLearningOutboxEntity();
        job.setId(UUID.randomUUID());
        job.setJobType(LearningJobType.ONLINE_METRICS_ROLLUP);
        job.setPayload(Map.of(
                "grain", "DAY",
                "windowStart", "2026-09-16T00:00:00Z",
                "windowEnd", "2026-09-17T00:00:00Z"
        ));
        handler.handle(job);
        verify(metricsService).rollup(
                OnlineMetricGrain.DAY,
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-09-17T00:00:00Z")
        );

        GuidanceLearningOutboxEntity bare = new GuidanceLearningOutboxEntity();
        bare.setJobType(LearningJobType.ONLINE_METRICS_ROLLUP);
        bare.setPayload(Map.of());
        handler.handle(bare);
        verify(metricsService).rollup(
                OnlineMetricGrain.HOUR,
                Instant.parse("2026-09-16T16:00:00Z"),
                Instant.parse("2026-09-16T17:00:00Z")
        );
    }
}
