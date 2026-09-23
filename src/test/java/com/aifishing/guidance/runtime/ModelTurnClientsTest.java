package com.aifishing.guidance.runtime;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelTurnClientsTest {

    @Test
    void deterministicIsUsedOnlyWhenModeIsDeterministic() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);

        ModelTurnClient client = ModelTurnClients.create(properties, new OpenAiProperties(), null, Clock.systemUTC());

        assertThat(client).isInstanceOf(DeterministicModelTurnClient.class);
        assertThat(client.provider()).isEqualTo(DeterministicModelTurnClient.PROVIDER);
    }

    @Test
    void openaiModeDoesNotFallBackToDeterministic() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setApiKey("sk-test");
        openAi.setModel("gpt-4o");

        ModelTurnClient client = ModelTurnClients.create(
                properties,
                openAi,
                new OpenAiResponsesClient(openAi, new ObjectMapper()),
                Clock.systemUTC()
        );

        assertThat(client).isInstanceOf(OpenAiResponsesModelTurnClient.class);
        assertThat(client).isNotInstanceOf(DeterministicModelTurnClient.class);
        assertThat(client.provider()).isEqualTo(OpenAiResponsesModelTurnClient.PROVIDER);
    }

    @Test
    void missingModeDoesNotDefaultToDeterministic() {
        assertThatThrownBy(() -> ModelTurnClients.create(
                new GuidanceProperties(), new OpenAiProperties(), null, Clock.systemUTC()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime-mode");
    }

    @Test
    void openaiAdapterReportsConfigErrorInsteadOfDeterministicStay() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.OPENAI);
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setModel("gpt-4o");
        OpenAiResponsesModelTurnClient client = new OpenAiResponsesModelTurnClient(
                openAi,
                new OpenAiResponsesClient(openAi, new ObjectMapper()),
                properties,
                Clock.systemUTC()
        );

        ModelTurnResult result = client.nextTurn(new ModelTurnInput(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                com.aifishing.guidance.contracts.GuidanceTrigger.USER_REQUEST,
                com.aifishing.guidance.GuidancePhase2Fixtures.safeState(),
                null,
                null,
                java.util.List.of(),
                0,
                null
        ));

        assertThat(result).isInstanceOf(ModelTurnResult.Error.class);
        assertThat(((ModelTurnResult.Error) result).errorType()).isEqualTo("CONFIG");
    }

    @Test
    void openaiInstructionsIncludePackageCooldownAndStayBiteLine() {
        assertThat(OpenAiResponsesModelTurnClient.instructions()).contains(
                "Prefer STAY at the current stop. Do not MOVE to a cooled package or oscillate A→B→A"
        );
        assertThat(OpenAiResponsesModelTurnClient.instructions()).contains(
                "BITE/FISH_ON here support STAY only"
        );
        assertThat(OpenAiResponsesModelTurnClient.instructions())
                .isEqualTo(com.aifishing.guidance.versions.PromptProfile.guidanceV1().instructions());
    }
}
