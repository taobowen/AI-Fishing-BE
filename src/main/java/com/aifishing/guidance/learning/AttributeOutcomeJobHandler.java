package com.aifishing.guidance.learning;

import com.aifishing.guidance.attribution.OutcomeAttributor;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import com.aifishing.guidance.metrics.OnlineMetricWindows;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AttributeOutcomeJobHandler implements LearningJobHandler {

    private final OutcomeAttributor outcomeAttributor;
    private final GuidanceLearningOutboxService learningOutboxService;
    private final Clock clock;

    public AttributeOutcomeJobHandler(
            OutcomeAttributor outcomeAttributor,
            @Lazy GuidanceLearningOutboxService learningOutboxService,
            Clock clock
    ) {
        this.outcomeAttributor = outcomeAttributor;
        this.learningOutboxService = learningOutboxService;
        this.clock = clock;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.ATTRIBUTE_OUTCOME;
    }

    @Override
    @Transactional
    public void handle(GuidanceLearningOutboxEntity job) {
        UUID sessionId = job.getFishingSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("ATTRIBUTE_OUTCOME requires fishingSessionId");
        }
        outcomeAttributor.attribute(sessionId);
        learningOutboxService.enqueue(
                sessionId,
                LearningJobType.REFLECTION_EVAL,
                "reflection-eval:" + sessionId + ":" + job.getId(),
                Map.of("sourceJobId", job.getId().toString())
        );
        enqueueRollup(sessionId, job.getId(), OnlineMetricGrain.HOUR);
        enqueueRollup(sessionId, job.getId(), OnlineMetricGrain.DAY);
    }

    private void enqueueRollup(UUID sessionId, UUID sourceJobId, OnlineMetricGrain grain) {
        Instant windowStart = OnlineMetricWindows.start(clock.instant(), grain);
        Instant windowEnd = OnlineMetricWindows.end(windowStart, grain);
        String source = sourceJobId == null ? "unknown" : sourceJobId.toString();
        learningOutboxService.enqueue(
                sessionId,
                LearningJobType.ONLINE_METRICS_ROLLUP,
                "online-metrics-rollup:" + grain.name() + ":" + windowStart + ":" + source,
                Map.of(
                        "grain", grain.name(),
                        "windowStart", windowStart.toString(),
                        "windowEnd", windowEnd.toString(),
                        "sourceJobId", source
                )
        );
    }
}
