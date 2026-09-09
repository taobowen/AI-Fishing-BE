package com.aifishing.fishingsession.dto;

import java.util.List;
import java.util.UUID;

public record StartFishingSessionRequest(
        UUID tripPlanId,
        LateStartMode lateStart,
        List<UUID> skipVisitIds
) {
    public enum LateStartMode {
        SKIP_EXPIRED
    }
}
