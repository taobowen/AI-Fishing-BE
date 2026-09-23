package com.aifishing.planning.environment;

import com.aifishing.lake.domain.Lake;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TripClockTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Test
    void sameDayUsesStartDateWhenEndTimeIsLater() {
        TripClock.Window window = TripClock.resolve(DAY, null, LocalTime.of(6, 0), LocalTime.of(15, 0), TORONTO);
        assertThat(window.plannedEndDate()).isEqualTo(DAY);
        assertThat(window.start().toLocalDate()).isEqualTo(DAY);
        assertThat(window.end().toLocalDate()).isEqualTo(DAY);
        assertThat(window.duration()).isEqualTo(Duration.ofHours(9));
        assertThat(window.endAfterStart()).isTrue();
        assertThat(window.withinMaxDuration()).isTrue();
    }

    @Test
    void omittedEndDateInfersNextDayWhenEndTimeIsNotAfterStart() {
        TripClock.Window window = TripClock.resolve(DAY, null, LocalTime.of(20, 0), LocalTime.of(5, 0), TORONTO);
        assertThat(window.plannedEndDate()).isEqualTo(DAY.plusDays(1));
        assertThat(window.end().toLocalDate()).isEqualTo(DAY.plusDays(1));
        assertThat(window.end().toLocalTime()).isEqualTo(LocalTime.of(5, 0));
        assertThat(window.duration()).isEqualTo(Duration.ofHours(9));
        assertThat(window.withinMaxDuration()).isTrue();
    }

    @Test
    void explicitEndDateIsUsedExactly() {
        TripClock.Window window = TripClock.resolve(
                DAY, DAY.plusDays(1), LocalTime.of(20, 0), LocalTime.of(5, 0), TORONTO);
        assertThat(window.plannedEndDate()).isEqualTo(DAY.plusDays(1));
        assertThat(window.endAt()).isEqualTo(window.end().toInstant());
    }

    @Test
    void exactTwentyFourHoursIsAccepted() {
        TripClock.Window window = TripClock.resolve(
                DAY, DAY.plusDays(1), LocalTime.of(6, 0), LocalTime.of(6, 0), TORONTO);
        assertThat(window.duration()).isEqualTo(Duration.ofHours(24));
        assertThat(window.withinMaxDuration()).isTrue();
        assertThat(window.endAfterStart()).isTrue();
    }

    @Test
    void moreThanTwentyFourHoursIsRejected() {
        TripClock.Window window = TripClock.resolve(
                DAY, DAY.plusDays(1), LocalTime.of(6, 0), LocalTime.of(6, 1), TORONTO);
        assertThat(window.withinMaxDuration()).isFalse();
    }

    @Test
    void tripAndLakeResolveUsesPersistedEndDate() {
        Trip trip = new Trip();
        trip.setPlannedDate(DAY);
        trip.setPlannedEndDate(DAY.plusDays(1));
        trip.setFishingStartTime(LocalTime.of(22, 0));
        trip.setFishingEndTime(LocalTime.of(4, 0));
        Lake lake = new Lake();
        lake.setTimeZoneId("America/Toronto");
        assertThat(TripClock.end(trip, lake).toLocalDate()).isEqualTo(DAY.plusDays(1));
        assertThat(TripClock.startAt(trip, lake)).isBefore(TripClock.endAt(trip, lake));
    }
}
