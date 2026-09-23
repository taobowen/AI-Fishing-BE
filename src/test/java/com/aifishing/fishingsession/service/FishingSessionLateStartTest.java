package com.aifishing.fishingsession.service;

import com.aifishing.planning.domain.TripWaypoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FishingSessionLateStartTest {

    @Test
    void waypointBeforeOvernightEndIsNotExpiredAndAfterEndIsExpired() {
        Instant end = Instant.parse("2026-09-19T09:00:00Z");
        TripWaypoint before = new TripWaypoint();
        before.setPlannedDepartureAt(end.minusSeconds(60));
        TripWaypoint after = new TripWaypoint();
        after.setPlannedDepartureAt(end.plusSeconds(60));

        assertThat(FishingSessionService.isExpired(before, end.minusSeconds(120))).isFalse();
        assertThat(FishingSessionService.isExpired(before, end)).isTrue();
        assertThat(FishingSessionService.isExpired(after, end)).isFalse();
        assertThat(FishingSessionService.isExpired(after, end.plusSeconds(120))).isTrue();
    }
}
