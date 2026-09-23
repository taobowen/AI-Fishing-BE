package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record InferredUserPreference(
        String schemaVersion,
        UUID userId,
        String key,
        String value,
        int evidenceCount,
        double confidence,
        Instant firstObservedAt,
        Instant lastObservedAt
) {
}
