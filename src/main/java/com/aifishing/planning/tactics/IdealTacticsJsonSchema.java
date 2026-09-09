package com.aifishing.planning.tactics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IdealTacticsJsonSchema {

    public static final String NAME = "trip_ideal_tactics";

    private static final List<String> FAMILIES = List.of(
            "PADDLETAIL", "MINNOW_SOFT_PLASTIC", "GRUB", "TUBE", "NED_RIG", "DROP_SHOT_BAIT",
            "JIG", "JERKBAIT", "CRANKBAIT", "LIPLESS_CRANKBAIT", "SPINNERBAIT", "CHATTERBAIT",
            "SPOON", "INLINE_SPINNER", "TOPWATER", "FROG", "TEXAS_RIG", "CAROLINA_RIG", "BUZZBAIT", "OTHER"
    );
    private static final List<String> LENGTHS = List.of("UNDER_3_IN", "3_TO_4_IN", "4_TO_5_IN", "OVER_5_IN");
    private static final List<String> WEIGHTS = List.of(
            "UNDER_1_8", "1_8_TO_1_4", "1_4_TO_3_8", "3_8_TO_1_2", "1_2_TO_3_4", "OVER_3_4"
    );
    private static final List<String> COLORS = List.of(
            "NATURAL", "WHITE_PEARL", "SILVER", "GOLD", "GREEN_PUMPKIN", "BROWN", "BLACK",
            "DARK", "CHARTREUSE", "FIRETIGER", "BRIGHT", "TRANSLUCENT", "OTHER"
    );
    private static final List<String> PRESENTATIONS = List.of(
            "STEADY_RETRIEVE", "TWITCH_PAUSE", "STOP_AND_GO", "WALK_THE_DOG", "POP_AND_PAUSE",
            "BUZZ", "LIFT_DROP", "HOP_ALONG_BOTTOM", "DRAG_AND_SHAKE", "SWIM_NEAR_BOTTOM",
            "YO_YO", "DEAD_STICK", "SLOW_ROLL", "BURN", "VERTICAL_JIG", "CAST_ACROSS_CONTOUR", "OTHER"
    );

    private IdealTacticsJsonSchema() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stops", array(stop()));
        return object(List.of("stops"), properties);
    }

    private static Map<String, Object> stop() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("visitId", Map.of("type", "string"));
        properties.put("idealTactic", tactic(true));
        properties.put("acceptableAlternativeTactics", array(tactic(false)));
        return object(List.of("visitId", "idealTactic", "acceptableAlternativeTactics"), properties);
    }

    private static Map<String, Object> tactic(boolean requiredPathCue) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("lureFamily", enumString(FAMILIES));
        properties.put("lengthBand", nullableEnum(LENGTHS));
        properties.put("lengthInches", nullableNumber());
        properties.put("weightBand", nullableEnum(WEIGHTS));
        properties.put("weightOz", nullableNumber());
        properties.put("preferredColors", array(enumString(COLORS)));
        properties.put("optionalWeight", nullableNumber());
        properties.put("presentationTechnique", enumString(PRESENTATIONS));
        properties.put("instructions", Map.of("type", "string"));
        properties.put("rationale", Map.of("type", "string"));
        properties.put("retrieveSpeed", nullableString());
        properties.put("presentationDepth", nullableString());
        properties.put("pathCue", nullableString());
        List<String> required = new ArrayList<>(List.of(
                "lureFamily", "lengthBand", "lengthInches", "weightBand", "weightOz",
                "preferredColors", "optionalWeight", "presentationTechnique",
                "instructions", "rationale", "retrieveSpeed", "presentationDepth", "pathCue"
        ));
        if (!requiredPathCue) {
            return object(required, properties);
        }
        return object(required, properties);
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
        return Map.of("type", "array", "items", items);
    }

    private static Map<String, Object> enumString(List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", values);
        return schema;
    }

    private static Map<String, Object> nullableEnum(List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", List.of("string", "null"));
        schema.put("enum", values);
        return schema;
    }

    private static Map<String, Object> nullableNumber() {
        return Map.of("type", List.of("number", "null"));
    }

    private static Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }
}
