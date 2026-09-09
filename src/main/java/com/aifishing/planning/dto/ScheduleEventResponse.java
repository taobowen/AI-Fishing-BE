package com.aifishing.planning.dto;

import java.time.Instant;

public record ScheduleEventResponse(
        String type,
        Instant from,
        Instant to,
        String location,
        Integer afterWaypointSequence,
        Integer minutes
) {
}
