package com.aifishing.common.openai;

import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    public OpenAiResponsesRequest.Result complete(OpenAiResponsesRequest request) {
        if (!openAiProperties.isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY / app.openai.api-key is not configured");
        }
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .baseUrl("https://api.openai.com/v1")
                    .build();
            String response = client.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .body(objectMapper.writeValueAsString(body(request)))
                    .retrieve()
                    .body(String.class);
            return parse(response, request);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI Responses call failed: {}", ex.getMessage());
            throw new IllegalStateException("OpenAI Responses call failed: " + truncate(ex.getMessage()), ex);
        }
    }

    Map<String, Object> body(OpenAiResponsesRequest request) {
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", request.schemaName());
        format.put("strict", request.strict());
        format.put("schema", request.schema());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_output_tokens", request.maxOutputTokens());
        body.put("instructions", request.instructions());
        body.put("input", request.input());
        body.put("text", Map.of("format", format));
        if (request.webSearch()) {
            body.put("tools", List.of(Map.of("type", "web_search")));
            body.put("tool_choice", "auto");
        }
        return body;
    }

    public OpenAiResponsesRequest.Result parse(String response, OpenAiResponsesRequest request) throws Exception {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        return new OpenAiResponsesRequest.Result(
                unwrapJson(extractOutputText(root)),
                usage(root),
                citations(root),
                textOr(root.path("model"), request.model()),
                textOr(root.path("id"), null),
                request.webSearch()
        );
    }

    private String extractOutputText(JsonNode root) {
        String outputText = root.path("output_text").asText(null);
        if (outputText != null && !outputText.isBlank()) {
            return outputText;
        }
        JsonNode output = root.path("output");
        StringBuilder builder = new StringBuilder();
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String text = part.path("text").asText(null);
                        if (text == null || text.isBlank()) {
                            text = part.path("output_text").asText(null);
                        }
                        if (text != null && !text.isBlank()) {
                            if (!builder.isEmpty()) {
                                builder.append('\n');
                            }
                            builder.append(text);
                        }
                    }
                }
            }
        }
        if (builder.isEmpty()) {
            throw new IllegalStateException("OpenAI response did not include output text");
        }
        return builder.toString();
    }

    private List<OpenAiResponsesRequest.Citation> citations(JsonNode root) {
        List<OpenAiResponsesRequest.Citation> citations = new ArrayList<>();
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return citations;
        }
        for (JsonNode item : output) {
            collectCitations(item.path("content"), citations);
            if ("web_search_call".equals(item.path("type").asText()) || "web_search".equals(item.path("type").asText())) {
                JsonNode results = item.path("action").path("sources");
                if (results.isArray()) {
                    for (JsonNode source : results) {
                        citations.add(new OpenAiResponsesRequest.Citation(
                                textOr(source.path("title"), null),
                                textOr(source.path("url"), null)
                        ));
                    }
                }
            }
        }
        return citations;
    }

    private void collectCitations(JsonNode content, List<OpenAiResponsesRequest.Citation> citations) {
        if (!content.isArray()) {
            return;
        }
        for (JsonNode part : content) {
            JsonNode annotations = part.path("annotations");
            if (!annotations.isArray()) {
                continue;
            }
            for (JsonNode annotation : annotations) {
                String url = textOr(annotation.path("url"), textOr(annotation.path("uri"), null));
                if (url != null) {
                    citations.add(new OpenAiResponsesRequest.Citation(textOr(annotation.path("title"), null), url));
                }
            }
        }
    }

    private Map<String, Object> usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (usage.has("input_tokens")) {
            map.put("inputTokens", usage.path("input_tokens").asInt());
        }
        if (usage.has("output_tokens")) {
            map.put("outputTokens", usage.path("output_tokens").asInt());
        }
        if (usage.has("total_tokens")) {
            map.put("totalTokens", usage.path("total_tokens").asInt());
        }
        return map;
    }

    private String unwrapJson(String json) {
        if (json == null) {
            return "{}";
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (newline > 0 && end > newline) {
                return trimmed.substring(newline + 1, end).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(openAiProperties.getTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(openAiProperties.getTimeoutSeconds()));
        return factory;
    }

    private String textOr(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
