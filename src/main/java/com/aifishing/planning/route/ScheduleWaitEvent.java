package com.aifishing.planning.route;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScheduleWaitEvent(
        Instant from,
        Instant to,
        String location,
        Integer afterWaypointSequence,
        int minutes
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "WAIT");
        map.put("from", from == null ? null : from.toString());
        map.put("to", to == null ? null : to.toString());
        map.put("location", location);
        if (afterWaypointSequence != null) {
            map.put("afterWaypointSequence", afterWaypointSequence);
        }
        map.put("minutes", minutes);
        return map;
    }
}
