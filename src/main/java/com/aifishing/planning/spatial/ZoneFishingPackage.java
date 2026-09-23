package com.aifishing.planning.spatial;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Best feasible local fishing package for one duration budget.
 * Independent per budget: the 45-minute sequence is not required to be a prefix
 * of the 180-minute sequence.
 */
public record ZoneFishingPackage(
        int visitMinutes,
        int fishingMinutes,
        int localTransitMinutes,
        int waitMinutes,
        double localDistanceM,
        double marginalUtility,
        List<UUID> consumedMemberIds,
        ZoneSubPlan subPlan
) {
    public ZoneFishingPackage {
        consumedMemberIds = consumedMemberIds == null ? List.of() : List.copyOf(consumedMemberIds);
    }

    public String sequenceFingerprint() {
        if (consumedMemberIds.isEmpty()) {
            return "";
        }
        return consumedMemberIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    public static ZoneFishingPackage from(ZoneSubPlan plan, List<UUID> consumedMemberIds, double localDistanceM) {
        if (plan == null) {
            return null;
        }
        return new ZoneFishingPackage(
                plan.visitMinutesOrFallback(),
                plan.fishingMinutes(),
                plan.internalTransitMinutes(),
                plan.waitMinutes(),
                localDistanceM,
                plan.utility(),
                consumedMemberIds,
                plan
        );
    }
}
