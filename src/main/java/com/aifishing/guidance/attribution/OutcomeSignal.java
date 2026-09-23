package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.OutcomeKind;

import java.time.Instant;
import java.util.UUID;

public record OutcomeSignal(
        UUID eventId,
        Instant occurredAt,
        OutcomeKind kind,
        UUID fishInteractionId
) {
}
