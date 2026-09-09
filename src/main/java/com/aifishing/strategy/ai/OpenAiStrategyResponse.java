package com.aifishing.strategy.ai;

import java.util.List;
import java.util.Map;

public record OpenAiStrategyResponse(
        String json,
        Map<String, Object> usageMetadata,
        Map<String, Object> sourceMetadata
) {
    public OpenAiStrategyResponse {
        usageMetadata = copy(usageMetadata);
        sourceMetadata = copy(sourceMetadata);
    }

    private static Map<String, Object> copy(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    public static OpenAiStrategyResponse empty() {
        return new OpenAiStrategyResponse("{}", Map.of(), Map.of());
    }

    public record Citation(String title, String url) {
    }

    public static Map<String, Object> sources(String model, List<Citation> citations, boolean webSearch) {
        return Map.of(
                "model", model == null ? "" : model,
                "webSearch", webSearch,
                "citations", citations == null ? List.of() : citations
        );
    }
}
