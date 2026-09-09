package com.aifishing.strategy.ai;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.common.openai.OpenAiResponsesRequest;
import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiFishingStrategyClient implements FishingStrategyReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiFishingStrategyClient.class);

    private final OpenAiProperties openAiProperties;
    private final StrategyPromptFactory promptFactory;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesClient responsesClient;

    public OpenAiFishingStrategyClient(
            OpenAiProperties openAiProperties,
            StrategyPromptFactory promptFactory,
            ObjectMapper objectMapper,
            OpenAiResponsesClient responsesClient
    ) {
        this.openAiProperties = openAiProperties;
        this.promptFactory = promptFactory;
        this.objectMapper = objectMapper;
        this.responsesClient = responsesClient;
    }

    @Override
    public StrategyReasonerResult reason(FishingContext context, List<String> previousValidationErrors) {
        try {
            OpenAiResponsesRequest.Result parsed = responsesClient.complete(new OpenAiResponsesRequest(
                    openAiProperties.getStrategyModel(),
                    openAiProperties.getMaxTokens(),
                    promptFactory.systemPrompt(),
                    promptFactory.userPrompt(context, previousValidationErrors),
                    StrategyJsonSchema.NAME,
                    StrategyJsonSchema.schema(),
                    openAiProperties.isWebSearchEnabled()
            ));
            FishingStrategyProfile profile = parseProfile(parsed.json());
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("model", parsed.model());
            source.put("webSearch", parsed.webSearch());
            source.put("citations", parsed.citations());
            source.put("promptVersion", promptFactory.version());
            source.put("id", parsed.responseId());
            return new StrategyReasonerResult(profile, parsed.usage(), source);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI strategy call failed: {}", ex.getMessage());
            throw new IllegalStateException("OpenAI strategy call failed: " + truncate(ex.getMessage()), ex);
        }
    }

    OpenAiStrategyResponse parseResponse(String response) throws Exception {
        OpenAiResponsesRequest.Result parsed = responsesClient.parse(response, new OpenAiResponsesRequest(
                openAiProperties.getStrategyModel(),
                openAiProperties.getMaxTokens(),
                "",
                "",
                StrategyJsonSchema.NAME,
                StrategyJsonSchema.schema(),
                openAiProperties.isWebSearchEnabled()
        ));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("model", parsed.model());
        source.put("webSearch", parsed.webSearch());
        source.put("citations", parsed.citations());
        source.put("promptVersion", promptFactory.version());
        source.put("id", parsed.responseId());
        return new OpenAiStrategyResponse(parsed.json(), parsed.usage(), source);
    }

    FishingStrategyProfile parseProfile(String json) throws Exception {
        FishingStrategyProfile profile = objectMapper.readValue(json, FishingStrategyProfile.class);
        return new FishingStrategyProfile(
                profile.targetSpecies(),
                profile.structurePreferences(),
                profile.timeWindows(),
                profile.generalTechniques(),
                profile.weatherInterpretation(),
                profile.modelConfidence(),
                null,
                profile.warnings(),
                profile.dataLimitations()
        );
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
