package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionSummary(
        String schemaVersion,
        UUID sessionId,
        String summaryText,
        List<String> keyFacts,
        Instant createdAt
) {
}
