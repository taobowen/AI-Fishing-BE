package com.aifishing.fishingsession.dto;

public record AdminFishingSessionResponse(
        FishingSessionResponse session,
        long locationPointCount,
        long acceptedPointCount,
        long clientEventCount
) {
}
