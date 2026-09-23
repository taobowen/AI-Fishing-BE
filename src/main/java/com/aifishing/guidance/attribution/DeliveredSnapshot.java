package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.DeliveredDecision;

import java.time.Instant;
import java.util.UUID;

public record DeliveredSnapshot(
        UUID deliveredEntityId,
        UUID runId,
        Instant deliveredAt,
        DeliveredDecision decision
) {
}
