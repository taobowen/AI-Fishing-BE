package com.aifishing.guidance.contracts;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ToolRequestEnvelope(
        String schemaVersion,
        ToolName toolName,
        JsonNode arguments,
        Instant requestedAt
) {
}
