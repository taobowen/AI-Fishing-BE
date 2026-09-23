package com.aifishing.trip.api;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record PastTripResponse(
        UUID tripId,
        UUID tripPlanId,
        UUID lakeId,
        String lakeName,
        String lakeCardImageUrl,
        FishSpecies primaryTargetSpecies,
        Instant plannedStartAt,
        Instant plannedEndAt,
        FishingSessionStatus sessionStatus,
        UUID completedSessionId,
        boolean resultsAvailable,
        int visitedStops,
        int totalStops
) {
}
