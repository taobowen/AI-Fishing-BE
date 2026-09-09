package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.render.RenderedTile;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiVisionClient implements VisionMapClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVisionClient.class);

    private final OpenAiProperties openAiProperties;
    private final VisionProperties visionProperties;
    private final VisionFeatureParser parser;
    private final ObjectMapper objectMapper;

    public OpenAiVisionClient(
            OpenAiProperties openAiProperties,
            VisionProperties visionProperties,
            VisionFeatureParser parser,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.visionProperties = visionProperties;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<VisionCandidate> extract(RenderedTile tile, AnalysisContext context) {
        if (!openAiProperties.isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY / app.openai.api-key is not configured");
        }
        String prompt = VisionPrompt.forTile(context, tile.georef(), visionProperties);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(tile.png());
        Map<String, Object> body = Map.of(
                "model", openAiProperties.getModel(),
                "max_tokens", openAiProperties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        )
                ))
        );
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .baseUrl("https://api.openai.com/v1")
                    .build();
            String response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("{}");
            return parser.parse(content, tile.georef());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI vision call failed: {}", ex.getMessage());
            throw new IllegalStateException("OpenAI vision call failed: " + ex.getMessage(), ex);
        }
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(openAiProperties.getTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(openAiProperties.getTimeoutSeconds()));
        return factory;
    }
}
