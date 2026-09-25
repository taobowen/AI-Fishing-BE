package com.aifishing.analytics.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TrackAnalyticsEventResponse(
        UUID id,
        String name,
        Map<String, Object> properties,
        Instant createdAt
) {
}
