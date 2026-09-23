package com.aifishing.fishingsession.service;

import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.planning.domain.TripWaypoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMapperRecommendedWindowTest {

    private static final Instant ARRIVED = Instant.parse("2026-09-18T14:30:00Z");
    private static final Instant FISHING_STARTED = Instant.parse("2026-09-18T14:40:00Z");

    @Test
    void prefersFishingStartedAtFromMetadataOverArrivedAt() {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setArrivedAt(ARRIVED);
        row.setMetadata(Map.of("fishingStartedAt", FISHING_STARTED.toString()));

        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setPlannedFishingMinutes(45);

        Instant start = SessionMapper.recommendedFishingStart(row);
        assertThat(start).isEqualTo(FISHING_STARTED);
        assertThat(SessionMapper.recommendedFishingEnd(start, waypoint))
                .isEqualTo(FISHING_STARTED.plusSeconds(45 * 60));
    }

    @Test
    void fallsBackToArrivedAtWhenFishingStartedAtIsAbsent() {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setArrivedAt(ARRIVED);

        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setPlannedFishingMinutes(45);

        Instant start = SessionMapper.recommendedFishingStart(row);
        assertThat(start).isEqualTo(ARRIVED);
        assertThat(SessionMapper.recommendedFishingEnd(start, waypoint))
                .isEqualTo(ARRIVED.plusSeconds(45 * 60));
    }

    @Test
    void recommendedWindowIsNullBeforeArrival() {
        SessionWaypointProgress row = new SessionWaypointProgress();
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setPlannedFishingMinutes(45);

        Instant start = SessionMapper.recommendedFishingStart(row);
        assertThat(start).isNull();
        assertThat(SessionMapper.recommendedFishingEnd(start, waypoint)).isNull();
    }
}
