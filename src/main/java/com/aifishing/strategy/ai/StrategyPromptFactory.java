package com.aifishing.strategy.ai;

import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.strategy.context.FishingContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StrategyPromptFactory {

    public static final String VERSION = "fishing-strategy-v1";

    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public StrategyPromptFactory(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    public String version() {
        String configured = openAiProperties.getStrategyPromptVersion();
        return configured == null || configured.isBlank() ? VERSION : configured;
    }

    public String systemPrompt() {
        return """
                You are a freshwater fishing strategist. Produce a FishingStrategyProfile JSON object that matches the provided schema.

                Hard rules:
                - Do not choose GPS coordinates, waypoints, or named navigation directions (northeast hump, go to the south shore, etc.).
                - Do not mention or invent LakeFeature IDs, UUIDs, or lake geometry.
                - Prefer structure types that are available in the lake summary. Do not invent structures the pipeline did not find.
                - Dataset status NOT_AVAILABLE or NOT_CHECKED means we lack data, not that the lake has none of that feature in nature.
                - Do not decide whether fishing is legal. Do not quote or invent regulation text or sanctuary boundaries. Legal filtering is a later deterministic step.
                - Air temperature is not observed water temperature. If waterTemperatureAvailable is false, do not treat air temp as water temp and do not invent a numeric water temperature.
                - Prefer techniques the angler already has gear for when gear is listed. If gear is empty, still recommend generic techniques.
                - timeWindows[].structurePreferences and timeWindows[].techniques are the in-window plan (effective weights for that window).
                - Optional timeWindows[].lightPreference is the in-window solar-exposure preference: SUN_EXPOSED, SHADE_PREFERRED, TRANSITION_PREFERRED, or NEUTRAL. Omit or use NEUTRAL if unsure. This is not a request to invent shade maps, true shadows, or water temperature.
                - Global structurePreferences and generalTechniques are day-level priors / fallbacks, not averaged with window weights.
                - Global structurePreferences and generalTechniques are day-level priors / fallbacks, not averaged with window weights.
                - Use the same array representation globally and in windows: [{type, weight, rationale}]. Never emit a map of weights.
                - Weights are 0–1. Depth min must be <= max. Keep time windows inside the trip fishing hours; do not overlap interiors.
                - Rationales must be concise. Include modelConfidence as a self-score 0–1 if you can; omit systemConfidence (the backend computes it).
                - You may echo dataLimitations as warnings, but do not invent official species presence or water temperature.
                """;
    }

    public String userPrompt(FishingContext context, List<String> previousValidationErrors) {
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            json = String.valueOf(context);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Fishing context (summaries only; no geometries, no regulation raw text):\n");
        builder.append(json);
        builder.append("\n\nReturn only the StrategyProfile object.");
        if (previousValidationErrors != null && !previousValidationErrors.isEmpty()) {
            builder.append("\n\nThe previous response failed validation. Fix these errors and return a new object:\n");
            for (String error : previousValidationErrors) {
                builder.append("- ").append(error).append('\n');
            }
        }
        return builder.toString();
    }
}
