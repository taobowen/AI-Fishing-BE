package com.aifishing.guidance.contracts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ToolName {
    GET_NEARBY_WAYPOINTS("get_nearby_waypoints"),
    GET_WAYPOINT_STRUCTURE("get_waypoint_structure"),
    GET_LIVE_WAYPOINT_ACTIVITY("get_live_waypoint_activity"),
    GET_HISTORICAL_PERFORMANCE("get_historical_performance"),
    GET_FISHING_KNOWLEDGE("get_fishing_knowledge"),
    GET_ALTERNATIVE_ROUTE("get_alternative_route");

    private final String wire;

    ToolName(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static ToolName fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ToolName value : values()) {
            if (value.wire.equals(raw) || value.name().equals(raw)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown tool name: " + raw);
    }
}
