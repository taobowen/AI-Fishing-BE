package com.aifishing.planning.environment;

import com.aifishing.lake.domain.Lake;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.trip.domain.Trip;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TripClock {

    public static final Duration MAX_DURATION = Duration.ofHours(24);

    private TripClock() {
    }

    public record Window(
            LocalDate plannedDate,
            LocalDate plannedEndDate,
            LocalTime startTime,
            LocalTime endTime,
            ZoneId zone,
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        public Instant startAt() {
            return start.toInstant();
        }

        public Instant endAt() {
            return end.toInstant();
        }

        public Duration duration() {
            return Duration.between(start, end);
        }

        public boolean endAfterStart() {
            return end.isAfter(start);
        }

        public boolean withinMaxDuration() {
            return duration().compareTo(MAX_DURATION) <= 0;
        }
    }

    public static ZoneId zoneId(Lake lake) {
        String id = lake == null ? null : lake.getTimeZoneId();
        if (id == null || id.isBlank()) {
            return ZoneId.of("UTC");
        }
        return ZoneId.of(id);
    }

    public static ZoneId zoneId(PlanningContext context) {
        return zoneId(context == null ? null : context.lake());
    }

    public static ZoneId zoneId(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return ZoneId.of("UTC");
        }
        return ZoneId.of(timeZoneId);
    }

    public static LocalDate inferEndDate(LocalDate plannedDate, LocalTime startTime, LocalTime endTime) {
        if (plannedDate == null || startTime == null || endTime == null) {
            return plannedDate;
        }
        return endTime.isAfter(startTime) ? plannedDate : plannedDate.plusDays(1);
    }

    public static Window resolve(
            LocalDate plannedDate,
            LocalDate plannedEndDate,
            LocalTime startTime,
            LocalTime endTime,
            ZoneId zone
    ) {
        ZoneId resolvedZone = zone == null ? ZoneId.of("UTC") : zone;
        LocalDate endDate = plannedEndDate != null
                ? plannedEndDate
                : inferEndDate(plannedDate, startTime, endTime);
        ZonedDateTime start = ZonedDateTime.of(plannedDate, startTime, resolvedZone);
        ZonedDateTime end = ZonedDateTime.of(endDate, endTime, resolvedZone);
        return new Window(plannedDate, endDate, startTime, endTime, resolvedZone, start, end);
    }

    public static Window resolve(Trip trip, Lake lake) {
        return resolve(
                trip.getPlannedDate(),
                trip.getPlannedEndDate(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime(),
                zoneId(lake)
        );
    }

    public static ZonedDateTime start(Trip trip, Lake lake) {
        return resolve(trip, lake).start();
    }

    public static ZonedDateTime end(Trip trip, Lake lake) {
        return resolve(trip, lake).end();
    }

    public static Instant startAt(PlanningContext context) {
        return start(context.trip(), context.lake()).toInstant();
    }

    public static Instant endAt(PlanningContext context) {
        return end(context.trip(), context.lake()).toInstant();
    }

    public static Instant startAt(Trip trip, Lake lake) {
        return start(trip, lake).toInstant();
    }

    public static Instant endAt(Trip trip, Lake lake) {
        return end(trip, lake).toInstant();
    }

    public static ZonedDateTime zoned(Instant instant, Lake lake) {
        return instant.atZone(zoneId(lake));
    }

    public static LocalTime localTime(Instant instant, Lake lake) {
        return zoned(instant, lake).toLocalTime();
    }

    public static ZonedDateTime atOrAfter(ZonedDateTime anchor, LocalTime time) {
        ZonedDateTime sameDay = anchor.toLocalDate().atTime(time).atZone(anchor.getZone());
        if (!sameDay.isBefore(anchor)) {
            return sameDay;
        }
        return sameDay.plusDays(1);
    }
}
