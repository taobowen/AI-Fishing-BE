package com.aifishing.guidance;

import com.aifishing.lake.processing.OpenAiProperties;
import org.springframework.stereotype.Component;

@Component
public class GuidanceRuntimeConfigGuard {

    public GuidanceRuntimeConfigGuard(GuidanceProperties guidance, OpenAiProperties openAi) {
        validate(guidance, openAi);
    }

    static void validate(GuidanceProperties guidance, OpenAiProperties openAi) {
        if (guidance == null || guidance.getRuntimeMode() == null) {
            throw new IllegalStateException(
                    "app.guidance.runtime-mode must be set explicitly to OPENAI or DETERMINISTIC"
            );
        }
        if (guidance.getRuntimeMode() != GuidanceProperties.RuntimeMode.OPENAI) {
            return;
        }
        if (openAi == null || !openAi.isConfigured()) {
            throw new IllegalStateException(
                    "app.guidance.runtime-mode=OPENAI requires OPENAI_API_KEY / app.openai.api-key"
            );
        }
        if (openAi.getModel() == null || openAi.getModel().isBlank()) {
            throw new IllegalStateException(
                    "app.guidance.runtime-mode=OPENAI requires app.openai.model"
            );
        }
    }
}
