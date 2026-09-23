package com.aifishing.guidance.learning;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.common.openai.OpenAiResponsesRequest;
import com.aifishing.guidance.contracts.SessionSummary;
import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SessionSummaryLlmClient {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryLlmClient.class);

    private final OpenAiProperties openAiProperties;
    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    public SessionSummaryLlmClient(
            OpenAiProperties openAiProperties,
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    public SessionSummary summarize(List<String> keyFacts, String extractiveText) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("summaryText", "keyFacts"));
        schema.put("properties", Map.of(
                "summaryText", Map.of("type", "string"),
                "keyFacts", Map.of("type", "array", "items", Map.of("type", "string"))
        ));
        OpenAiResponsesRequest.Result result = responsesClient.complete(new OpenAiResponsesRequest(
                openAiProperties.getModel(),
                400,
                "Summarize this completed fishing session in one short paragraph. "
                        + "Keep only extractive facts; do not invent hypotheses or advice.",
                "Extractive facts:\n" + String.join("\n", keyFacts)
                        + "\n\nExtractive draft:\n" + extractiveText,
                "session_summary",
                schema,
                false
        ));
        return parse(result.json(), keyFacts, extractiveText);
    }

    SessionSummary parse(String json, List<String> fallbackFacts, String fallbackText) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            String summary = root.path("summaryText").asText("");
            List<String> facts = new ArrayList<>();
            if (root.path("keyFacts").isArray()) {
                root.path("keyFacts").forEach(node -> {
                    String text = node.asText("");
                    if (!text.isBlank()) {
                        facts.add(text);
                    }
                });
            }
            if (summary.isBlank()) {
                return null;
            }
            return new SessionSummary(null, null, summary, facts.isEmpty() ? fallbackFacts : facts, null);
        } catch (Exception ex) {
            log.warn("Session summary LLM parse failed: {}", ex.getMessage());
            return new SessionSummary(null, null, fallbackText, fallbackFacts, null);
        }
    }
}
