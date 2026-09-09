package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class BoatCapabilityPromptFactory {

    private final BoatCapabilityProperties properties;
    private final ObjectMapper objectMapper;

    public BoatCapabilityPromptFactory(BoatCapabilityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String version() {
        return properties.getPromptVersion();
    }

    public String systemPrompt() {
        return """
                You estimate fishing-trip PLANNING heuristics for a small inland boat: cruise speed (km/h), \
                practical round-trip range (km, nullable), and a LOW/MEDIUM/HIGH wind/open-water capability class.

                Read the free-text description first. Also extract boat type, propulsion, and motors when mentioned \
                so equipment validation still works.

                If priorMetrics are present, REVISE those values using the new description. Do not ignore priors \
                unless the new text clearly contradicts them. Do not start from a blank extract when priors exist. \
                A battery or fuel note should mainly revise range and confidence, and may only nudge speed.

                This is NOT certified speed, endurance, seaworthiness, or boating safety.
                Do not choose fishing spots, routes, GPS coordinates, or legal decisions.
                Do not convert thrust pounds into fake horsepower.
                Prefer manufacturer/public specifications when web search is available.
                Forum claims are weak evidence: lower confidence.
                If battery or fuel endurance is unknown, set practicalRangeKm.value to null and low confidence.
                If a metric is missing or low-confidence, list extractionGaps and missingHints \
                (HP/thrust, battery Ah or fuel litres, hull size / open-water use). Do not invent values.
                Keep reasoning summaries to one short sentence each.
                """;
    }

    public String userPrompt(Boat boat, List<String> previousErrors) {
        return userPrompt(boat, previousErrors, null);
    }

    public String userPrompt(Boat boat, List<String> previousErrors, BoatCapabilityPriors priors) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configurationDescription", boat.getConfigurationDescription());
        if (priors != null && priors.present()) {
            payload.put("priorMetrics", priors.toMap());
            payload.put(
                    "revisionInstruction",
                    "Revise priorMetrics from the new description. Do not discard them unless the text clearly contradicts."
            );
        }
        payload.put("boatType", boat.getType() == null ? null : boat.getType().name());
        payload.put("manufacturer", boat.getManufacturer());
        payload.put("model", boat.getModel());
        payload.put("year", boat.getYear());
        payload.put("propulsionTypes", boat.getPropulsionTypes());
        payload.put("primaryTransitPropulsionType", boat.getPrimaryTransitPropulsionType());
        payload.put("motors", boat.getMotors() == null ? List.of() : boat.getMotors().stream().map(this::motor).toList());
        payload.put("sanitizedEquipmentFacts", EquipmentFactsSanitizer.extract(boat.getConfigurationDescription()));
        if (boat.getMaxSpeedKmh() != null) {
            payload.put("legacyReportedMaxSpeedKmh", boat.getMaxSpeedKmh());
            payload.put("legacyMaxSpeedNote", "Weak, untrusted; do not treat as measured cruise.");
        }
        if (previousErrors != null && !previousErrors.isEmpty()) {
            payload.put("previousValidationErrors", previousErrors);
        }
        try {
            String lead = priors != null && priors.present()
                    ? "Revise planning capability from this free-text description and priorMetrics JSON:\n"
                    : "Estimate planning capability from this free-text description JSON:\n";
            return lead + objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize boat capability prompt", ex);
        }
    }

    private Map<String, Object> motor(BoatMotor motor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("propulsionType", motor.propulsionType());
        map.put("manufacturer", motor.manufacturer());
        map.put("model", motor.model());
        map.put("horsepower", motor.horsepower());
        map.put("thrustLb", motor.thrustLb());
        return map;
    }
}
