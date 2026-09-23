package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record SemanticMemory(
        String schemaVersion,
        UUID memoryId,
        UUID userId,
        SemanticMemoryKind kind,
        String text,
        String embeddingRef,
        Instant createdAt
) {
}
