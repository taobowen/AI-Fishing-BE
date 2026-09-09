package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.common.openai.OpenAiResponsesRequest;
import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiBoatCapabilityClient implements BoatCapabilityReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiBoatCapabilityClient.class);

    private final OpenAiProperties openAiProperties;
    private final BoatCapabilityProperties capabilityProperties;
    private final BoatCapabilityPromptFactory promptFactory;
    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    public OpenAiBoatCapabilityClient(
            OpenAiProperties openAiProperties,
            BoatCapabilityProperties capabilityProperties,
            BoatCapabilityPromptFactory promptFactory,
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.capabilityProperties = capabilityProperties;
        this.promptFactory = promptFactory;
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result estimate(Boat boat, List<String> previousValidationErrors) {
        return estimate(boat, previousValidationErrors, null);
    }

    @Override
    public Result estimate(Boat boat, List<String> previousValidationErrors, BoatCapabilityPriors priors) {
        if (!openAiProperties.isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY / app.openai.api-key is not configured");
        }
        try {
            String model = capabilityProperties.getModel() == null || capabilityProperties.getModel().isBlank()
                    ? openAiProperties.getStrategyModel()
                    : capabilityProperties.getModel();
            OpenAiResponsesRequest.Result parsed = responsesClient.complete(new OpenAiResponsesRequest(
                    model,
                    openAiProperties.getMaxTokens(),
                    promptFactory.systemPrompt(),
                    promptFactory.userPrompt(boat, previousValidationErrors, priors),
                    BoatCapabilityJsonSchema.NAME,
                    BoatCapabilityJsonSchema.schema(),
                    capabilityProperties.isWebSearchEnabled()
            ));
            AiBoatCapabilityEstimate estimate = objectMapper.readValue(parsed.json(), AiBoatCapabilityEstimate.class);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("citations", parsed.citations());
            evidence.put("webSearch", parsed.webSearch());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("usage", parsed.usage());
            raw.put("responseId", parsed.responseId());
            raw.put("reasoningSummary", estimate.reasoningSummary());
            if (estimate.evidence() != null) {
                raw.put("structuredEvidence", estimate.evidence());
            }
            return new Result(estimate, parsed.webSearch(), parsed.model(), evidence, raw);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Boat capability OpenAI call failed: {}", ex.getMessage());
            throw new IllegalStateException("Boat capability OpenAI call failed", ex);
        }
    }
}
