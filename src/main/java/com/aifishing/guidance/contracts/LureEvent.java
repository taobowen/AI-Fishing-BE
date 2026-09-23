package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;

import java.time.Instant;

public record LureEvent(
        String schemaVersion,
        Instant occurredAt,
        LureFamily lureFamily,
        PresentationTechnique presentation,
        Double depthM,
        RetrieveStyle retrieveStyle
) {
}
