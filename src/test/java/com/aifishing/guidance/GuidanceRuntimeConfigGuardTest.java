package com.aifishing.guidance;

import com.aifishing.lake.processing.OpenAiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidanceRuntimeConfigGuardTest {

    @Test
    void missingRuntimeModeFailsStartup() {
        assertThatThrownBy(() -> GuidanceRuntimeConfigGuard.validate(new GuidanceProperties(), new OpenAiProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime-mode");
    }

    @Test
    void openaiWithoutKeyFailsStartup() {
        GuidanceProperties guidance = new GuidanceProperties();
        guidance.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setApiKey("  ");
        openAi.setModel("gpt-4o");

        assertThatThrownBy(() -> GuidanceRuntimeConfigGuard.validate(guidance, openAi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void openaiWithoutModelFailsStartup() {
        GuidanceProperties guidance = new GuidanceProperties();
        guidance.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setApiKey("sk-test");
        openAi.setModel(" ");

        assertThatThrownBy(() -> GuidanceRuntimeConfigGuard.validate(guidance, openAi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.openai.model");
    }

    @Test
    void openaiWithKeyAndModelPasses() {
        GuidanceProperties guidance = new GuidanceProperties();
        guidance.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setApiKey("sk-test");
        openAi.setModel("gpt-4o");

        assertThatCode(() -> GuidanceRuntimeConfigGuard.validate(guidance, openAi)).doesNotThrowAnyException();
    }

    @Test
    void deterministicDoesNotRequireOpenAiKey() {
        GuidanceProperties guidance = new GuidanceProperties();
        guidance.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);

        assertThatCode(() -> GuidanceRuntimeConfigGuard.validate(guidance, new OpenAiProperties()))
                .doesNotThrowAnyException();
    }
}
