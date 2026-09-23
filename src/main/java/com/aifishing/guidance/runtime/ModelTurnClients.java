package com.aifishing.guidance.runtime;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.lake.processing.OpenAiProperties;

import java.time.Clock;
import java.util.Objects;

/**
 * Explicit runtime-mode factory. Missing OPENAI config is a startup-guard
 * failure, not a silent switch to {@link DeterministicModelTurnClient}.
 */
public final class ModelTurnClients {

    private ModelTurnClients() {
    }

    public static ModelTurnClient create(
            GuidanceProperties guidanceProperties,
            OpenAiProperties openAiProperties,
            OpenAiResponsesClient responsesClient,
            Clock clock
    ) {
        Objects.requireNonNull(guidanceProperties, "guidanceProperties");
        if (guidanceProperties.getRuntimeMode() == null) {
            throw new IllegalStateException(
                    "app.guidance.runtime-mode must be set explicitly to OPENAI or DETERMINISTIC"
            );
        }
        return switch (guidanceProperties.getRuntimeMode()) {
            case DETERMINISTIC -> new DeterministicModelTurnClient();
            case OPENAI -> new OpenAiResponsesModelTurnClient(
                    Objects.requireNonNull(openAiProperties, "openAiProperties"),
                    Objects.requireNonNull(responsesClient, "responsesClient"),
                    guidanceProperties,
                    Objects.requireNonNull(clock, "clock")
            );
        };
    }
}
