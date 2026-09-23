package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import com.aifishing.guidance.metrics.OnlineMetricWindows;
import com.aifishing.guidance.metrics.OnlineOutcomeMetricsService;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class OnlineMetricsRollupJobHandler implements LearningJobHandler {

    private final OnlineOutcomeMetricsService metricsService;
    private final Clock clock;

    public OnlineMetricsRollupJobHandler(OnlineOutcomeMetricsService metricsService, Clock clock) {
        this.metricsService = metricsService;
        this.clock = clock;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.ONLINE_METRICS_ROLLUP;
    }

    @Override
    @Transactional
    public void handle(GuidanceLearningOutboxEntity job) {
        Map<String, Object> payload = job.getPayload() == null ? Map.of() : job.getPayload();
        OnlineMetricGrain grain = grain(payload.get("grain"));
        Instant windowStart = instant(payload.get("windowStart"));
        Instant windowEnd = instant(payload.get("windowEnd"));
        if (windowStart == null) {
            windowStart = OnlineMetricWindows.start(clock.instant(), grain);
        }
        if (windowEnd == null) {
            windowEnd = OnlineMetricWindows.end(windowStart, grain);
        }
        metricsService.rollup(grain, windowStart, windowEnd);
    }

    private static OnlineMetricGrain grain(Object raw) {
        if (raw == null) {
            return OnlineMetricGrain.HOUR;
        }
        return OnlineMetricGrain.valueOf(String.valueOf(raw).trim().toUpperCase());
    }

    private static Instant instant(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Instant.parse(text);
    }
}
