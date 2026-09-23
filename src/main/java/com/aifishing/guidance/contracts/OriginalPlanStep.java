package com.aifishing.guidance.contracts;

import java.util.List;
import java.util.UUID;

/**
 * Immutable original trip-plan stop. Physical and plan-step ids do not change;
 * {@code progressStatus} is a session overlay rebuilt each snapshot.
 */
public record OriginalPlanStep(
        int sequence,
        UUID tripWaypointId,
        UUID physicalZoneId,
        UUID fishingTargetId,
        UUID lakeFeatureId,
        List<UUID> packageMemberIds,
        String progressStatus
) {
    public OriginalPlanStep {
        packageMemberIds = packageMemberIds == null ? List.of() : List.copyOf(packageMemberIds);
    }
}
