package com.aifishing.planning.service;

import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.PlannedStopMembers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan-step fields stored on {@code TripWaypoint.metadata} without changing {@code visit_kind}.
 */
public final class TripWaypointPlanMetadata {

    public static final String MACRO_VISIT_KIND = "macroVisitKind";
    public static final String PACKAGE_MEMBER_IDS = "packageMemberIds";

    private TripWaypointPlanMetadata() {
    }

    public static void put(Map<String, Object> metadata, PlannedStop stop) {
        if (metadata == null || stop == null) {
            return;
        }
        if (stop.visitKind() != null) {
            metadata.put(MACRO_VISIT_KIND, stop.visitKind().name());
        }
        List<UUID> members = PlannedStopMembers.packageMemberIds(stop);
        if (members.isEmpty()) {
            return;
        }
        metadata.put(PACKAGE_MEMBER_IDS, members.stream().map(UUID::toString).toList());
    }
}
