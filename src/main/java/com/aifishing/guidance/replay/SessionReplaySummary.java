package com.aifishing.guidance.replay;

import com.aifishing.common.enums.FishSpecies;

import java.time.Instant;
import java.util.UUID;

public record SessionReplaySummary(
        UUID sessionId,
        UUID lakeId,
        String lakeName,
        FishSpecies species,
        Instant startedAt,
        Instant endedAt,
        String productionPolicyVersion,
        String candidatePolicyVersion,
        int fishingEffortSeconds,
        long biteCount,
        long fishOnCount,
        long catchLandedCount,
        long catchLostCount
) {
}
