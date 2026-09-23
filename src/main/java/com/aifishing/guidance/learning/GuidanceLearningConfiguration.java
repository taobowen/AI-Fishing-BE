package com.aifishing.guidance.learning;

import com.aifishing.guidance.GuidanceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class GuidanceLearningConfiguration {

    @Bean(name = "guidanceLearningExecutor")
    TaskExecutor guidanceLearningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("guidance-learning-");
        executor.initialize();
        return executor;
    }

    @Configuration
    static class GuidanceLearningSchedules {
        private final GuidanceProperties properties;
        private final GuidanceLearningOutboxClaimer claimer;

        GuidanceLearningSchedules(GuidanceProperties properties, GuidanceLearningOutboxClaimer claimer) {
            this.properties = properties;
            this.claimer = claimer;
        }

        @Scheduled(fixedDelayString = "${app.guidance.learning-outbox.poll-interval-ms:15000}")
        void pollLearningOutbox() {
            if (!properties.getLearningOutbox().isEnabled()) {
                return;
            }
            claimer.drain();
        }
    }
}
