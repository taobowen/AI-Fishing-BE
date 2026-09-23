package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.UUID;

public record AgentReflection(
        String schemaVersion,
        UUID sessionId,
        UUID runId,
        ReflectionClaimKind claimKind,
        ReflectionCauseKind causeKind,
        String text,
        Instant createdAt
) {
}
