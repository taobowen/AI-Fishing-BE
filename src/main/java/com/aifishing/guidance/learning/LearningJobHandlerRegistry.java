package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.spi.LearningJobHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LearningJobHandlerRegistry {

    private final EnumMap<LearningJobType, LearningJobHandler> handlers;

    public LearningJobHandlerRegistry(List<LearningJobHandler> handlers) {
        this.handlers = new EnumMap<>(LearningJobType.class);
        for (LearningJobHandler handler : handlers) {
            if (handler == null || handler.jobType() == null) {
                continue;
            }
            LearningJobHandler previous = this.handlers.put(handler.jobType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate learning handler for " + handler.jobType());
            }
        }
    }

    public Optional<LearningJobHandler> find(LearningJobType jobType) {
        return Optional.ofNullable(handlers.get(jobType));
    }

    public Set<LearningJobType> supported() {
        return handlers.isEmpty() ? EnumSet.noneOf(LearningJobType.class) : EnumSet.copyOf(handlers.keySet());
    }
}
