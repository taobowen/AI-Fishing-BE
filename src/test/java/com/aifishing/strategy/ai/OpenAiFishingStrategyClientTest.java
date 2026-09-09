package com.aifishing.strategy.ai;

import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiFishingStrategyClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OpenAiFishingStrategyClient client = new OpenAiFishingStrategyClient(
            new OpenAiProperties(),
            new StrategyPromptFactory(new OpenAiProperties(), objectMapper),
            objectMapper,
            new com.aifishing.common.openai.OpenAiResponsesClient(new OpenAiProperties(), objectMapper)
    );

    @Test
    void schemaOmitsSystemConfidenceAndUsesArrays() {
        Map<String, Object> schema = StrategyJsonSchema.schema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("modelConfidence", "structurePreferences", "timeWindows");
        @SuppressWarnings("unchecked")
        Map<String, Object> timeWindows = (Map<String, Object>) properties.get("timeWindows");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) timeWindows.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> windowProps = (Map<String, Object>) items.get("properties");
        assertThat(windowProps).containsKey("lightPreference");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) items.get("required");
        assertThat(required).contains("lightPreference");
        @SuppressWarnings("unchecked")
        Map<String, Object> light = (Map<String, Object>) windowProps.get("lightPreference");
        assertThat(light.get("type")).isEqualTo(List.of("string", "null"));
        assertThat(properties).doesNotContainKey("systemConfidence");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void parseResponseReadsOutputTextAndStripsSystemConfidence() throws Exception {
        String response = """
                {
                  "id": "resp_1",
                  "model": "gpt-4o",
                  "output_text": "{\\"targetSpecies\\":[{\\"species\\":\\"SMALLMOUTH_BASS\\",\\"priority\\":1}],\\"structurePreferences\\":[{\\"type\\":\\"HUMP\\",\\"weight\\":0.9,\\"rationale\\":null}],\\"timeWindows\\":[{\\"from\\":\\"06:00:00\\",\\"to\\":\\"10:00:00\\",\\"preferredDepthM\\":{\\"min\\":2,\\"max\\":4},\\"structurePreferences\\":[],\\"techniques\\":[{\\"type\\":\\"NED_RIG\\",\\"weight\\":0.8,\\"rationale\\":null}]}],\\"generalTechniques\\":[],\\"weatherInterpretation\\":{\\"summary\\":\\"Cloudy\\",\\"confidence\\":0.5},\\"modelConfidence\\":0.77,\\"systemConfidence\\":0.12,\\"warnings\\":[],\\"dataLimitations\\":[]}",
                  "usage": {"input_tokens": 10, "output_tokens": 20, "total_tokens": 30}
                }
                """;
        OpenAiStrategyResponse parsed = client.parseResponse(response);
        FishingStrategyProfile profile = client.parseProfile(parsed.json());
        assertThat(profile.modelConfidence()).isEqualTo(0.77);
        assertThat(profile.systemConfidence()).isNull();
        assertThat(profile.timeWindows()).hasSize(1);
        assertThat(parsed.usageMetadata()).containsEntry("totalTokens", 30);
    }

    @Test
    void parseResponseReadsNestedOutputContent() throws Exception {
        String response = """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "{\\"targetSpecies\\":[],\\"structurePreferences\\":[],\\"timeWindows\\":[],\\"generalTechniques\\":[],\\"warnings\\":[],\\"dataLimitations\\":[]}"}
                      ]
                    }
                  ]
                }
                """;
        OpenAiStrategyResponse parsed = client.parseResponse(response);
        FishingStrategyProfile profile = client.parseProfile(parsed.json());
        assertThat(profile.structurePreferences()).isEmpty();
    }

    @Test
    void missingOutputTextFails() {
        assertThatThrownBy(() -> client.parseResponse("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output text");
    }
}
