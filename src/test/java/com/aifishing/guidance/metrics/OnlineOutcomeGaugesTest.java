package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnlineOutcomeGaugesTest {

    @Test
    void publishesLowCardinalityGaugesAndRejectsSessionTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OnlineOutcomeGauges gauges = new OnlineOutcomeGauges(registry);
        Instant at = Instant.parse("2026-09-16T16:00:00Z");
        OnlineGuidanceMetrics metrics = new OnlineGuidanceMetrics(
                GuidanceSchemaVersion.VALUE,
                at,
                at.plusSeconds(3600),
                AttributionDimension.LOCATION,
                2,
                1,
                0,
                1,
                0,
                3,
                0.5,
                0.6,
                0.2,
                0.1,
                null,
                null,
                null,
                null,
                null,
                1800L,
                4.0,
                6.0
        );
        gauges.publish(OnlineMetricGrain.HOUR, new OnlineOutcomeSnapshot(
                metrics,
                new LandingAnalytics(1, 0),
                new OnlineOutcomeRawCounts(
                        2, 1, 0, 1, 0, 3, 1, 0, 1, 1, 2, 5, 2, 1800, 1, 0, OnlineOutcomeSafetyCounts.empty())
        ));

        assertThat(registry.getMeters()).isNotEmpty();
        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags())
                    .allMatch(tag -> OnlineOutcomeGauges.ALLOWED_TAGS.contains(tag.getKey()));
            assertThat(meter.getId().getTag("sessionId")).isNull();
            assertThat(meter.getId().getTag("userId")).isNull();
            assertThat(meter.getId().getTag("runId")).isNull();
        }
        assertThat(registry.get(OnlineOutcomeGauges.SUCCESS_COUNT)
                .tags("dimension", "location", "grain", "hour", "kind", "fish_on_success")
                .gauge()
                .value()).isEqualTo(2.0);
        assertThat(registry.get(OnlineOutcomeGauges.LANDING_COUNT)
                .tags("dimension", "location", "grain", "hour", "result", "landed")
                .gauge()
                .value()).isEqualTo(1.0);

        assertThatThrownBy(() -> OnlineOutcomeGauges.assertAllowed(Tags.of("sessionId", "s1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
        assertThat(OnlineOutcomeGauges.ALLOWED_TAGS).isEqualTo(Set.of("dimension", "grain", "kind", "result"));
    }

    @Test
    void missingRatesAreNanNotZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OnlineOutcomeGauges gauges = new OnlineOutcomeGauges(registry);
        Instant at = Instant.parse("2026-09-16T16:00:00Z");
        OnlineGuidanceMetrics metrics = new OnlineGuidanceMetrics(
                GuidanceSchemaVersion.VALUE,
                at,
                at.plusSeconds(3600),
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                null
        );
        gauges.publish(OnlineMetricGrain.DAY, new OnlineOutcomeSnapshot(
                metrics, new LandingAnalytics(0, 0), new OnlineOutcomeRawCounts(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, OnlineOutcomeSafetyCounts.empty())));

        assertThat(registry.get(OnlineOutcomeGauges.FOLLOW_THROUGH_RATE)
                .tags("dimension", "all", "grain", "day")
                .gauge()
                .value()).isNaN();
        assertThat(registry.get(OnlineOutcomeGauges.FISH_ON_PER_HOUR)
                .tags("dimension", "all", "grain", "day")
                .gauge()
                .value()).isNaN();
    }
}
