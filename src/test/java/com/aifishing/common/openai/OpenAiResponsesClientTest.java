package com.aifishing.common.openai;

import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponsesClientTest {

    @Test
    void structuredOutputCanDisableStrictForGuidanceContracts() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(new OpenAiProperties(), new ObjectMapper());
        Map<String, Object> body = client.body(new OpenAiResponsesRequest(
                "gpt-4o",
                200,
                "instructions",
                "{}",
                "CandidateDecision",
                Map.of("type", "object"),
                false,
                false
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> format = (Map<String, Object>) ((Map<String, Object>) body.get("text")).get("format");
        assertThat(format.get("name")).isEqualTo("CandidateDecision");
        assertThat(format.get("strict")).isEqualTo(false);
    }
}
