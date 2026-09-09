package com.aifishing.strategy.ai;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.strategy.domain.LightPreference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrategyJsonSchema {

    public static final String NAME = "fishing_strategy_profile";

    private StrategyJsonSchema() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("targetSpecies", array(targetSpeciesItem()));
        properties.put("structurePreferences", array(structurePreference()));
        properties.put("timeWindows", array(timeWindow()));
        properties.put("generalTechniques", array(techniquePreference()));
        properties.put("weatherInterpretation", weatherInterpretation());
        properties.put("modelConfidence", nullableNumber());
        properties.put("warnings", array(Map.of("type", "string")));
        properties.put("dataLimitations", array(dataLimitation()));
        return object(
                List.of(
                        "targetSpecies",
                        "structurePreferences",
                        "timeWindows",
                        "generalTechniques",
                        "weatherInterpretation",
                        "modelConfidence",
                        "warnings",
                        "dataLimitations"
                ),
                properties
        );
    }

    private static Map<String, Object> targetSpeciesItem() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("species", stringEnum(FishSpecies.class));
        properties.put("priority", Map.of("type", "integer", "minimum", 1));
        return object(List.of("species", "priority"), properties);
    }

    private static Map<String, Object> structurePreference() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", stringEnum(FeatureType.class));
        properties.put("weight", weight());
        properties.put("rationale", nullableString());
        return object(List.of("type", "weight", "rationale"), properties);
    }

    private static Map<String, Object> techniquePreference() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", stringEnum(TechniqueType.class));
        properties.put("weight", weight());
        properties.put("rationale", nullableString());
        return object(List.of("type", "weight", "rationale"), properties);
    }

    private static Map<String, Object> timeWindow() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("from", Map.of("type", "string", "pattern", "^\\d{2}:\\d{2}(:\\d{2})?$"));
        properties.put("to", Map.of("type", "string", "pattern", "^\\d{2}:\\d{2}(:\\d{2})?$"));
        properties.put("preferredDepthM", depthRange());
        properties.put("structurePreferences", array(structurePreference()));
        properties.put("techniques", array(techniquePreference()));
        properties.put("lightPreference", nullableEnum(LightPreference.class));
        return object(List.of("from", "to", "preferredDepthM", "structurePreferences", "techniques", "lightPreference"), properties);
    }

    private static Map<String, Object> depthRange() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("min", Map.of("type", "number"));
        properties.put("max", Map.of("type", "number"));
        return object(List.of("min", "max"), properties);
    }

    private static Map<String, Object> weatherInterpretation() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        properties.put("confidence", nullableNumber());
        return object(List.of("summary", "confidence"), properties);
    }

    private static Map<String, Object> dataLimitation() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", Map.of("type", "string"));
        properties.put("message", nullableString());
        return object(List.of("code", "message"), properties);
    }

    private static Map<String, Object> weight() {
        return Map.of("type", "number", "minimum", 0, "maximum", 1);
    }

    private static Map<String, Object> object(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", required);
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> stringEnum(Class<? extends Enum<?>> type) {
        List<String> values = new ArrayList<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            values.add(constant.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", values);
        return schema;
    }

    private static Map<String, Object> nullableNumber() {
        return Map.of("type", List.of("number", "null"));
    }

    private static Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    private static Map<String, Object> nullableEnum(Class<? extends Enum<?>> type) {
        Map<String, Object> schema = stringEnum(type);
        List<Object> values = new ArrayList<>(schema.containsKey("enum") ? (List<?>) schema.get("enum") : List.of());
        values.add(null);
        schema.put("enum", values);
        schema.put("type", List.of("string", "null"));
        return schema;
    }
}
