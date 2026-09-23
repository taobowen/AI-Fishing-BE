package com.aifishing.guidance.runtime;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.lake.processing.OpenAiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the provider-neutral model adapter from explicit {@code runtime-mode}.
 * Does not register {@code TriggerRouter} or {@code DerivedTriggerEvaluator}.
 * Does not add guidance MVC controllers.
 */
@Configuration
public class GuidanceRuntimeConfiguration {

    @Bean
    ModelTurnClient modelTurnClient(
            GuidanceProperties guidanceProperties,
            OpenAiProperties openAiProperties,
            OpenAiResponsesClient responsesClient,
            Clock clock
    ) {
        return ModelTurnClients.create(guidanceProperties, openAiProperties, responsesClient, clock);
    }
}
