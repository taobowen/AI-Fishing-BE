package com.aifishing.feedback.performance.dto;

import com.aifishing.common.enums.FishSpecies;

import java.util.UUID;

public record LakeEmpiricalSummaryResponse(
        UUID lakeId,
        FishSpecies primaryTargetSpecies,
        long completedSessions,
        long landedCount,
        double fishingEffortHours,
        Double rawLandedCpue
) {
}
