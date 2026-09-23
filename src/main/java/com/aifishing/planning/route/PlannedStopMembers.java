package com.aifishing.planning.route;

import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.planning.spatial.ZoneVisitState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Package members for one planned stop: this visit's incremental members, not the full zone window.
 */
public final class PlannedStopMembers {

    private PlannedStopMembers() {
    }

    public static List<UUID> packageMemberIds(PlannedStop stop) {
        if (stop == null) {
            return List.of();
        }
        ZoneFishingPackage pkg = stop.fishingPackage();
        if (pkg != null && !pkg.consumedMemberIds().isEmpty()) {
            return pkg.consumedMemberIds();
        }
        ZoneSubPlan subPlan = stop.zoneSubPlan();
        if (subPlan == null || subPlan.stops() == null || subPlan.stops().isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (ZoneSubPlan.MicroStop micro : subPlan.stops()) {
            if (micro == null || micro.spot() == null) {
                continue;
            }
            UUID id = ZoneVisitState.memberId(micro.spot());
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }
}
