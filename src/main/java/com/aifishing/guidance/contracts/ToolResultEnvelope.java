package com.aifishing.guidance.contracts;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ToolResultEnvelope(
        String schemaVersion,
        ToolResultStatus status,
        Instant observedAt,
        String source,
        JsonNode data,
        String errorType,
        Boolean retryable,
        String field
) {
}
