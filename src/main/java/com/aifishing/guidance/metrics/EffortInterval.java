package com.aifishing.guidance.metrics;

import java.time.Instant;
import java.util.UUID;

public record EffortInterval(
        UUID fishingSessionId,
        Instant start,
        Instant end
) {
}
