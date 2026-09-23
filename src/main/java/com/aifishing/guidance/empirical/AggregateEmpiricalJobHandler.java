package com.aifishing.guidance.empirical;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AggregateEmpiricalJobHandler implements LearningJobHandler {

    private final EmpiricalAggregationService aggregationService;

    public AggregateEmpiricalJobHandler(EmpiricalAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.AGGREGATE_EMPIRICAL;
    }

    @Override
    public void handle(GuidanceLearningOutboxEntity job) {
        UUID sessionId = job.getFishingSessionId();
        if (sessionId == null) {
            throw new IllegalArgumentException("AGGREGATE_EMPIRICAL requires fishingSessionId");
        }
        aggregationService.aggregateSession(sessionId);
    }
}
