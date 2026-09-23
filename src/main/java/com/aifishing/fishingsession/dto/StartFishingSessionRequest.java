package com.aifishing.fishingsession.dto;

import com.aifishing.fishingsession.domain.SessionGuidanceMode;

import java.util.List;
import java.util.UUID;

public record StartFishingSessionRequest(
        UUID tripPlanId,
        LateStartMode lateStart,
        List<UUID> skipVisitIds,
        SessionGuidanceMode guidanceMode
) {
    public StartFishingSessionRequest(UUID tripPlanId, LateStartMode lateStart, List<UUID> skipVisitIds) {
        this(tripPlanId, lateStart, skipVisitIds, null);
    }

    public enum LateStartMode {
        SKIP_EXPIRED,
        FOLLOW_FULL_PLAN
    }
}
