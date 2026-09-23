package com.aifishing.planning.spatial;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Point;

import java.util.List;

/**
 * Coarse zone prune only for clearly impossible water. Authoritative feasibility stays
 * route range + return-to-launch + reserve + existing hard safety.
 */
public final class ZoneReachability {

    public static final double CLEARLY_IMPOSSIBLE_FACTOR = 1.25;

    private ZoneReachability() {
    }

    public static boolean clearlyUnreachable(Point representative, PlanningContext context) {
        if (context == null || representative == null || context.routeStartPoint() == null) {
            return false;
        }
        if (context.warnings() != null && context.warnings().contains("BOAT_RANGE_LOW_CONFIDENCE")) {
            return false;
        }
        EffectiveBoatCapability effective = context.effectiveBoatCapability();
        if (effective == null || !effective.rangeEnforced() || effective.effectiveUsableRangeKm() == null) {
            return false;
        }
        double capKm = BoatCapabilityFilter.travelCapKm(context);
        if (!Double.isFinite(capKm) || capKm <= 0) {
            return false;
        }
        double distKm = GeoMetrics.distanceM(context.routeStartPoint(), representative) / 1000.0;
        return distKm > capKm * CLEARLY_IMPOSSIBLE_FACTOR;
    }

    public static boolean uncertainGeometry(List<?> portals) {
        return portals == null || portals.isEmpty();
    }
}
