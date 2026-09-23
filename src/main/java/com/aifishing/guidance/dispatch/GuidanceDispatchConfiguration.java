package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidanceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class GuidanceDispatchConfiguration {

    @Bean(name = "guidanceDispatchExecutor")
    TaskExecutor guidanceDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("guidance-dispatch-");
        executor.initialize();
        return executor;
    }

    @Configuration
    static class GuidanceDispatchSchedules {
        private final GuidanceProperties properties;
        private final GuidanceHeartbeatJob heartbeatJob;
        private final GuidanceTriggerOutboxClaimer claimer;

        GuidanceDispatchSchedules(
                GuidanceProperties properties,
                GuidanceHeartbeatJob heartbeatJob,
                GuidanceTriggerOutboxClaimer claimer
        ) {
            this.properties = properties;
            this.heartbeatJob = heartbeatJob;
            this.claimer = claimer;
        }

        @Scheduled(fixedDelayString = "${app.guidance.heartbeat-interval-ms:90000}")
        void heartbeat() {
            if (!properties.isDispatchEnabled()) {
                return;
            }
            heartbeatJob.tick();
        }

        @Scheduled(fixedDelayString = "${app.guidance.outbox.poll-interval-ms:15000}")
        void pollOutbox() {
            if (!properties.isDispatchEnabled()) {
                return;
            }
            claimer.drain();
        }
    }
}
