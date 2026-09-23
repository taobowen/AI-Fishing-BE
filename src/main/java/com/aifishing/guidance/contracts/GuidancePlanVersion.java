package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.List;

public record GuidancePlanVersion(
        String schemaVersion,
        int version,
        Integer parentVersion,
        String replanReason,
        ReplanScope replanScope,
        PlanCreatedBy createdBy,
        Instant createdAt,
        List<GuidancePlanStep> steps
) {
}
