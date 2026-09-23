package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.LiveWaypointActivity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Live occupancy projection. Phase 4 is Postgres geography; Phase 6 may swap Redis
 * without changing {@link LiveWaypointActivity}.
 */
public interface LiveWaypointActivityStore {

    Optional<LiveWaypointActivity> findNearby(UUID tripWaypointId, int radiusMeters, UUID excludeSessionId);

    void upsertLocation(
            UUID sessionId,
            double latitudeWgs84,
            double longitudeWgs84,
            Instant recordedAt,
            Double accuracyM,
            FishingActivityState activityState,
            UUID lakeId
    );

    void upsertActivityState(UUID sessionId, FishingActivityState activityState);
}
