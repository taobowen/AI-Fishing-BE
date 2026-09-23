package com.aifishing.guidance.obs;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidanceMetricsTest {

    @Test
    void recordsSeparateStageTimersWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GuidanceMetrics metrics = new GuidanceMetrics(registry);
        Tags tags = GuidanceMetrics.base(
                GuidanceTrigger.USER_REQUEST, AgentRunStatus.COMPLETED, false, "deterministic");

        metrics.record(GuidanceMetrics.ENVIRONMENT, tags, Duration.ofMillis(4));
        metrics.record(GuidanceMetrics.STATE, tags, Duration.ofMillis(5));
        metrics.record(GuidanceMetrics.SAFETY, tags, Duration.ofMillis(1));
        metrics.record(GuidanceMetrics.CONTEXT, tags, Duration.ofMillis(1));
        metrics.record(GuidanceMetrics.MODEL, tags, Duration.ofMillis(10));
        metrics.record(GuidanceMetrics.TOOLS, tags, Duration.ofMillis(2));
        metrics.record(GuidanceMetrics.VALIDATION, tags, Duration.ofMillis(1));
        metrics.record(GuidanceMetrics.PERSISTENCE, tags, Duration.ofMillis(1));
        metrics.record(GuidanceMetrics.TOTAL, tags, Duration.ofMillis(25));

        assertThat(registry.getMeters().stream().map(meter -> meter.getId().getName()))
                .containsExactlyInAnyOrder(
                        GuidanceMetrics.ENVIRONMENT,
                        GuidanceMetrics.STATE,
                        GuidanceMetrics.SAFETY,
                        GuidanceMetrics.CONTEXT,
                        GuidanceMetrics.MODEL,
                        GuidanceMetrics.TOOLS,
                        GuidanceMetrics.VALIDATION,
                        GuidanceMetrics.PERSISTENCE,
                        GuidanceMetrics.TOTAL
                );
        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags())
                    .allMatch(tag -> GuidanceMetrics.ALLOWED_TAGS.contains(tag.getKey()));
            assertThat(meter.getId().getTag("sessionId")).isNull();
            assertThat(meter.getId().getTag("runId")).isNull();
            assertThat(meter.getId().getTag("waypointId")).isNull();
            assertThat(meter.getId().getTag("traceId")).isNull();
        }
    }

    @Test
    void rejectsForbiddenTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GuidanceMetrics metrics = new GuidanceMetrics(registry);

        assertThatThrownBy(() -> metrics.record(
                GuidanceMetrics.TOTAL,
                Tags.of("sessionId", "550e8400-e29b-41d4-a716-446655440000"),
                Duration.ofMillis(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }
}
