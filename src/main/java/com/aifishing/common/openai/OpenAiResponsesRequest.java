package com.aifishing.common.openai;

import java.util.List;
import java.util.Map;

public record OpenAiResponsesRequest(
        String model,
        int maxOutputTokens,
        String instructions,
        String input,
        String schemaName,
        Map<String, Object> schema,
        boolean webSearch,
        boolean strict
) {
    public OpenAiResponsesRequest(
            String model,
            int maxOutputTokens,
            String instructions,
            String input,
            String schemaName,
            Map<String, Object> schema,
            boolean webSearch
    ) {
        this(model, maxOutputTokens, instructions, input, schemaName, schema, webSearch, true);
    }
    public record Citation(String title, String url) {
    }

    public record Result(
            String json,
            Map<String, Object> usage,
            List<Citation> citations,
            String model,
            String responseId,
            boolean webSearch
    ) {
    }
}
