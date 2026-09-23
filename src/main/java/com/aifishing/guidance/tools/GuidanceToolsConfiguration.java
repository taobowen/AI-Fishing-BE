package com.aifishing.guidance.tools;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.spi.AgentToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * Production tool runtime. Registers the six real {@link com.aifishing.guidance.spi.AgentTool}
 * implementations. Do not register {@code TriggerRouter} or {@code DerivedTriggerEvaluator} here.
 */
@Configuration
public class GuidanceToolsConfiguration {

    @Bean
    @Primary
    AgentToolRegistry agentToolRegistry(
            GetNearbyWaypointsTool getNearbyWaypointsTool,
            GetWaypointStructureTool getWaypointStructureTool,
            GetLiveWaypointActivityTool getLiveWaypointActivityTool,
            GetHistoricalPerformanceTool getHistoricalPerformanceTool,
            GetFishingKnowledgeTool getFishingKnowledgeTool,
            GetAlternativeRouteTool getAlternativeRouteTool
    ) {
        return new InMemoryAgentToolRegistry(
                getNearbyWaypointsTool,
                getWaypointStructureTool,
                getLiveWaypointActivityTool,
                getHistoricalPerformanceTool,
                getFishingKnowledgeTool,
                getAlternativeRouteTool
        );
    }

    @Bean
    AgentToolExecutor agentToolExecutor(
            AgentToolRegistry agentToolRegistry,
            GuidanceProperties guidanceProperties,
            Clock clock
    ) {
        return new AgentToolExecutor(agentToolRegistry, guidanceProperties, clock);
    }
}
