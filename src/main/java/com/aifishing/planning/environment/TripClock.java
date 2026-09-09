package com.aifishing.planning.environment;

import com.aifishing.lake.domain.Lake;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.trip.domain.Trip;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TripClock {

    private TripClock() {
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

    public static ZonedDateTime start(Trip trip, Lake lake) {
        return ZonedDateTime.of(trip.getPlannedDate(), trip.getFishingStartTime(), zoneId(lake));
    }

    public static ZonedDateTime end(Trip trip, Lake lake) {
        return ZonedDateTime.of(trip.getPlannedDate(), trip.getFishingEndTime(), zoneId(lake));
    }

    public static Instant startAt(PlanningContext context) {
        return start(context.trip(), context.lake()).toInstant();
    }

    public static Instant endAt(PlanningContext context) {
        return end(context.trip(), context.lake()).toInstant();
    }

    public static ZonedDateTime zoned(Instant instant, Lake lake) {
        return instant.atZone(zoneId(lake));
    }

    public static LocalTime localTime(Instant instant, Lake lake) {
        return zoned(instant, lake).toLocalTime();
    }
}
