package com.aifishing.guidance.contracts;

import java.util.UUID;

public record GuidanceCurrentResponse(
        UUID runId,
        DeliveredDecision decision
) {
}
