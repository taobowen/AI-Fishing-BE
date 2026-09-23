package com.aifishing.planning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlanningSearchConfigGuard {

    private static final Logger log = LoggerFactory.getLogger(PlanningSearchConfigGuard.class);

    public PlanningSearchConfigGuard(PlanningProperties properties) {
        if (properties != null && properties.getSchedule().getMaxWaypoints() > 0) {
            log.warn(
                    "app.planning.schedule.max-waypoints is deprecated and no longer caps search depth; "
                            + "use app.planning.search.hard-max-stops as the single product ceiling"
            );
        }
    }
}
