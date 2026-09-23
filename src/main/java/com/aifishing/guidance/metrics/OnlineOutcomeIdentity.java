package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;

import java.util.UUID;

/**
 * Global online-metrics identity. Role is not part of the key.
 */
public record OnlineOutcomeIdentity(
        UUID deliveredDecisionId,
        AttributionDimension attributionDimension,
        UUID fishInteractionId
) {
}
