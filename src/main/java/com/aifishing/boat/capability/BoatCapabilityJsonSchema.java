package com.aifishing.boat.capability;

import com.aifishing.common.enums.WindWaveCapability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoatCapabilityJsonSchema {

    public static final String NAME = "boat_capability_estimate";

    private BoatCapabilityJsonSchema() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("cruiseSpeedKmh", metricNumber(true));
        properties.put("practicalRangeKm", metricNumber(false));
        properties.put("windWaveCapability", metricEnum());
        properties.put("reasoningSummary", reasoning());
        properties.put("warnings", array(Map.of("type", "string")));
        properties.put("extractionGaps", array(Map.of("type", "string")));
        properties.put("missingHints", array(Map.of("type", "string")));
        properties.put("extractedType", Map.of(
                "type", List.of("string", "null"),
                "enum", List.of("FISHING_BOAT", "KAYAK", "CANOE", "PONTOON", "INFLATABLE", "OTHER")
        ));
        properties.put("extractedPropulsionTypes", array(Map.of(
                "type", "string",
                "enum", List.of("NONE", "PADDLE", "PEDAL", "ELECTRIC_TROLLING", "ELECTRIC_OUTBOARD", "GAS_OUTBOARD")
        )));
        properties.put("extractedMotors", array(extractedMotor()));
        properties.put("evidence", array(evidence()));
        properties.put("webEvidenceUsed", Map.of("type", "boolean"));
        return object(
                List.of(
                        "cruiseSpeedKmh",
                        "practicalRangeKm",
                        "windWaveCapability",
                        "reasoningSummary",
                        "warnings",
                        "extractionGaps",
                        "missingHints",
                        "evidence",
                        "webEvidenceUsed"
                ),
                properties
        );
    }

    private static Map<String, Object> metricNumber(boolean requiredValue) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("value", requiredValue ? Map.of("type", "number") : Map.of("type", List.of("number", "null")));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        return object(List.of("value", "confidence"), properties);
    }

    private static Map<String, Object> metricEnum() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> values = new ArrayList<>();
        for (WindWaveCapability value : WindWaveCapability.values()) {
            values.add(value.name());
        }
        properties.put("value", Map.of("type", "string", "enum", values));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        return object(List.of("value", "confidence"), properties);
    }

    private static Map<String, Object> reasoning() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("speed", Map.of("type", List.of("string", "null")));
        properties.put("range", Map.of("type", List.of("string", "null")));
        properties.put("windWave", Map.of("type", List.of("string", "null")));
        return object(List.of("speed", "range", "windWave"), properties);
    }

    private static Map<String, Object> extractedMotor() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("propulsionType", Map.of(
                "type", List.of("string", "null"),
                "enum", List.of("NONE", "PADDLE", "PEDAL", "ELECTRIC_TROLLING", "ELECTRIC_OUTBOARD", "GAS_OUTBOARD")
        ));
        properties.put("manufacturer", Map.of("type", List.of("string", "null")));
        properties.put("model", Map.of("type", List.of("string", "null")));
        properties.put("horsepower", Map.of("type", List.of("number", "null")));
        properties.put("thrustLb", Map.of("type", List.of("number", "null")));
        return object(List.of(), properties);
    }

    private static Map<String, Object> evidence() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string"));
        properties.put("description", Map.of("type", "string"));
        return object(List.of("type", "description"), properties);
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
}
