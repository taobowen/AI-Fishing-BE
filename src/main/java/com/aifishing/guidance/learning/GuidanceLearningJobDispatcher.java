package com.aifishing.guidance.learning;

import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dispatches only registered learning handlers. Unregistered types return
 * {@link LearningDispatchResult#UNHANDLED}; the claimer quarantines them.
 * Durable mutation jobs return {@link LearningDispatchResult#DEFERRED} when
 * {@code learningEnabled=false} so they stay pending.
 */
@Component
public class GuidanceLearningJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GuidanceLearningJobDispatcher.class);

    private final LearningJobHandlerRegistry registry;
    private final AgentRuntimeControlStore runtimeControlStore;

    public GuidanceLearningJobDispatcher(LearningJobHandlerRegistry registry) {
        this(registry, null);
    }

    @Autowired
    public GuidanceLearningJobDispatcher(
            LearningJobHandlerRegistry registry,
            AgentRuntimeControlStore runtimeControlStore
    ) {
        this.registry = registry;
        this.runtimeControlStore = runtimeControlStore;
    }

    public LearningDispatchResult dispatch(GuidanceLearningOutboxEntity job) {
        if (LearningMutationJobs.isDurableMutation(job.getJobType()) && !learningEnabled()) {
            log.debug("Deferring learning mutation {} {} while learningEnabled=false", job.getJobType(), job.getId());
            return LearningDispatchResult.DEFERRED;
        }
        LearningJobHandler handler = registry.find(job.getJobType()).orElse(null);
        if (handler == null) {
            log.debug("No learning handler for {} {}", job.getJobType(), job.getId());
            return LearningDispatchResult.UNHANDLED;
        }
        handler.handle(job);
        return LearningDispatchResult.HANDLED;
    }

    private boolean learningEnabled() {
        if (runtimeControlStore == null) {
            return true;
        }
        return runtimeControlStore.load().learningEnabled();
    }
}
