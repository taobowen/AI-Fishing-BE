package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;

/**
 * Learning outbox worker SPI. Unregistered job types must not be acked DONE.
 */
public interface LearningJobHandler {

    LearningJobType jobType();

    void handle(GuidanceLearningOutboxEntity job);
}
