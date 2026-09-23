package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.FeedbackStatus;

import java.time.Instant;
import java.util.UUID;

public record FeedbackObservation(
        UUID fishingSessionId,
        UUID deliveredDecisionId,
        FeedbackStatus status,
        Instant occurredAt
) {
}
