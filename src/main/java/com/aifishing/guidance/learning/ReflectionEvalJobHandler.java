package com.aifishing.guidance.learning;

import com.aifishing.guidance.attribution.ReflectionEvaluator;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ReflectionEvalJobHandler implements LearningJobHandler {

    private final ReflectionEvaluator reflectionEvaluator;

    public ReflectionEvalJobHandler(ReflectionEvaluator reflectionEvaluator) {
        this.reflectionEvaluator = reflectionEvaluator;
    }

    @Override
    public LearningJobType jobType() {
        return LearningJobType.REFLECTION_EVAL;
    }

    @Override
    @Transactional
    public void handle(GuidanceLearningOutboxEntity job) {
        UUID sessionId = job.getFishingSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("REFLECTION_EVAL requires fishingSessionId");
        }
        reflectionEvaluator.evaluate(sessionId);
    }
}
