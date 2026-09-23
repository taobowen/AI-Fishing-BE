package com.aifishing.fishingsession.dto;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;

import java.time.Instant;
import java.util.UUID;

public record NavigationResponse(
        UUID sessionId,
        FishingSessionStatus sessionStatus,
        WaypointProgressResponse currentWaypoint,
        GeoPointDto lastAcceptedLocation,
        Instant lastAcceptedAt,
        Double distanceM,
        Double bearingDegrees,
        UUID currentTransitLegId,
        FishingActivityState activityState,
        ActivityStateSource activityStateSource,
        AdHocFishingStopResponse adHocFishingStop,
        boolean stationaryFishingPrompt,
        UUID activeGuidanceTargetTripWaypointId
) {
}
