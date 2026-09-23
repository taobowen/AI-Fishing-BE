package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GetLiveWaypointActivityParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.runtime.AgentRunMdc;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Nearby live occupancy from {@link LiveWaypointActivityStore}. Unknown coords → UNKNOWN.
 * Never scans raw GPS points.
 */
@Component
public class GetLiveWaypointActivityTool implements AgentTool {

    static final int DEFAULT_RADIUS_METERS = 300;

    private final LiveWaypointActivityStore store;
    private final Clock clock;

    public GetLiveWaypointActivityTool(LiveWaypointActivityStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_LIVE_WAYPOINT_ACTIVITY;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetLiveWaypointActivityParams> parsed = AgentToolSupport.parse(
                request, name(), "GetLiveWaypointActivityParams", GetLiveWaypointActivityParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            GetLiveWaypointActivityParams params = parsed.params();
            int radiusMeters = params.radiusMeters() == null
                    ? DEFAULT_RADIUS_METERS
                    : params.radiusMeters();
            return store.findNearby(params.tripWaypointId(), radiusMeters, AgentRunMdc.sessionId())
                    .map(activity -> AgentToolSupport.ok(
                            name(), clock, GuidanceContracts.mapper().valueToTree(activity)))
                    .orElseGet(() -> AgentToolSupport.unknown(name(), clock));
        } catch (RuntimeException ex) {
            return AgentToolSupport.error(name(), clock);
        }
    }
}
