package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionEvent(
        String schemaVersion,
        SessionEventType type,
        Instant occurredAt,
        EventSource source,
        String idempotencyKey,
        Map<String, Object> payload,
        UUID fishInteractionId
) {
}
