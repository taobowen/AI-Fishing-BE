package com.aifishing.guidance.contracts;

import java.time.Instant;

public record ToolCallRecord(
        String schemaVersion,
        ToolRequestEnvelope request,
        ToolResultEnvelope result,
        Integer latencyMs,
        Instant observedAt
) {
}
