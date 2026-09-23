package com.aifishing.fishingsession.dto;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionResultsResponse(
        UUID sessionId,
        FishingSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Map<String, Object> summary,
        SessionPerformanceResponse performance
) {
}
