package com.aifishing.planning.tactics;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.common.openai.OpenAiResponsesRequest;
import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.TechniquePreference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IdealTacticsAiClient {

    private static final Logger log = LoggerFactory.getLogger(IdealTacticsAiClient.class);

    private final OpenAiProperties openAiProperties;
    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    public IdealTacticsAiClient(
            OpenAiProperties openAiProperties,
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return openAiProperties.isConfigured();
    }

    public List<StopTacticalProfile> recommend(List<FishableVisit> visits, PlanningContext context) {
        OpenAiResponsesRequest.Result parsed = responsesClient.complete(new OpenAiResponsesRequest(
                openAiProperties.getStrategyModel(),
                Math.min(4000, openAiProperties.getMaxTokens()),
                systemPrompt(),
                userPrompt(visits, context),
                IdealTacticsJsonSchema.NAME,
                IdealTacticsJsonSchema.schema(),
                false
        ));
        return parse(parsed.json(), visits);
    }

    List<StopTacticalProfile> parse(String json, List<FishableVisit> visits) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode stops = root.path("stops");
            Map<UUID, StopTacticalProfile> byId = new LinkedHashMap<>();
            if (stops.isArray()) {
                for (JsonNode node : stops) {
                    StopTacticalProfile profile = objectMapper.convertValue(node, StopTacticalProfile.class);
                    if (profile != null && profile.visitId() != null && profile.idealTactic() != null) {
                        byId.put(profile.visitId(), profile);
                    }
                }
            }
            List<StopTacticalProfile> out = new ArrayList<>();
            for (FishableVisit visit : visits) {
                StopTacticalProfile profile = byId.get(visit.visitId());
                if (profile != null) {
                    out.add(profile);
                }
            }
            if (out.size() != visits.size()) {
                throw new IllegalStateException("Tactical AI omitted " + (visits.size() - out.size()) + " visits");
            }
            return out;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse tactical AI response: {}", ex.getMessage());
            throw new IllegalStateException("Tactical AI parse failed", ex);
        }
    }

    private static String systemPrompt() {
        return """
                You recommend fishing lures for an already-finalized trip route.
                Do not change stops, order, or timing. Never mention user inventory or gear IDs.
                For each visit, pick one ideal lure family plus a short list of fishing-valid alternatives
                for the same species, structure, depth, and conditions.
                A frog is not an alternative for a drop-shot situation. Keep families honest.
                Size is family-aware: length for soft plastics and most hardbaits, weight for spoons,
                jigs, spinnerbaits, chatterbaits, inline spinners, and buzzbaits.
                For PATH visits, put contour-working language in pathCue and instructions.
                """;
    }

    private String userPrompt(List<FishableVisit> visits, PlanningContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (context != null && context.trip() != null) {
            payload.put("primarySpecies", context.trip().getPrimaryTargetSpecies());
            payload.put("secondarySpecies", context.trip().getSecondaryTargetSpecies());
            payload.put("fishingMode", context.trip().getFishingMode());
        }
        if (context != null && context.weather() != null) {
            payload.put("weather", Map.of(
                    "airTemperatureC", context.weather().airTemperatureC(),
                    "windSpeedKmh", context.weather().windSpeedKmh(),
                    "cloudCoverPercent", context.weather().cloudCoverPercent(),
                    "precipitationMm", context.weather().precipitationMm()
            ));
        }
        payload.put("strategyWindowTechniques", windowTechniques(context));
        payload.put("visits", visits.stream().map(this::visitPayload).toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize tactical prompt", ex);
        }
    }

    private Map<String, Object> visitPayload(FishableVisit visit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("visitId", visit.visitId().toString());
        map.put("targetKind", visit.targetKind());
        map.put("featureType", visit.featureType());
        map.put("representativeDepthM", visit.representativeDepthM());
        map.put("minDepthM", visit.minDepthM());
        map.put("maxDepthM", visit.maxDepthM());
        map.put("techniques", visit.techniques());
        map.put("arrivalAt", visit.arrivalAt());
        map.put("fishMinutes", visit.fishMinutes());
        map.put("pathLike", visit.pathLike());
        return map;
    }

    private static List<String> windowTechniques(PlanningContext context) {
        if (context == null || context.profile() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (TechniquePreference preference : context.profile().generalTechniques()) {
            if (preference != null && preference.type() != null) {
                names.add(preference.type().name());
            }
        }
        for (StrategyTimeWindow window : context.profile().timeWindows()) {
            if (window.techniques() == null) {
                continue;
            }
            for (TechniquePreference preference : window.techniques()) {
                if (preference != null && preference.type() != null) {
                    names.add(preference.type().name());
                }
            }
        }
        return names;
    }
}
