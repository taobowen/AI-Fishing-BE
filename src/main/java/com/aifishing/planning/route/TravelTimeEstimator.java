package com.aifishing.planning.route;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.RequestSpatialCache;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

@Component
public class TravelTimeEstimator {

    public TravelEstimate estimate(Point from, Point to, PlanningContext context) {
        if (from == null || to == null) {
            return TravelEstimate.unspecified();
        }
        PlanningProperties.Travel travel = context.properties().getTravel();
        double speedKmh = speedKmh(context);
        boolean returning = samePoint(to, context.routeStartPoint());
        RequestSpatialCache spatial = RequestSpatialCache.current();
        if (spatial != null && returning) {
            TravelEstimate cached = spatial.returnGeometry(
                    from, to, speedKmh, travel.getDetourFactor(), travel.getLandCrossingDetourFactor());
            if (cached != null) {
                return cached;
            }
        }
        double geodesicM = RequestSpatialCache.geodesicMeters(from, to);
        boolean landCrossing = context.geometry() != null && context.geometry().landCrossing(from, to);
        double factor = landCrossing ? travel.getLandCrossingDetourFactor() : travel.getDetourFactor();
        double minutes = geodesicM * factor / (speedKmh * 1000.0 / 60.0);
        TravelEstimate estimate = new TravelEstimate(geodesicM, minutes, factor, landCrossing);
        if (spatial != null && returning) {
            spatial.storeReturnGeometry(
                    from, to, speedKmh, travel.getDetourFactor(), travel.getLandCrossingDetourFactor(), estimate);
        }
        return estimate;
    }

    public TravelEstimate estimate(
            Point from,
            Point to,
            LakePlanningGeometry geometry,
            PlanningProperties properties,
            FishingMode mode,
            Double boatSpeedKmh
    ) {
        if (from == null || to == null) {
            return TravelEstimate.unspecified();
        }
        double geodesicM = RequestSpatialCache.geodesicMeters(from, to);
        boolean landCrossing = geometry != null && geometry.landCrossing(from, to);
        double factor = landCrossing
                ? properties.getTravel().getLandCrossingDetourFactor()
                : properties.getTravel().getDetourFactor();
        double speedKmh = mode == FishingMode.SHORE
                ? properties.getTravel().getDefaultShoreKmh()
                : (boatSpeedKmh == null ? properties.getTravel().getDefaultBoatKmh() : boatSpeedKmh);
        double minutes = geodesicM * factor / (speedKmh * 1000.0 / 60.0);
        return new TravelEstimate(geodesicM, minutes, factor, landCrossing);
    }

    private static double speedKmh(PlanningContext context) {
        if (context.fishingMode() == FishingMode.SHORE) {
            return context.properties().getTravel().getDefaultShoreKmh();
        }
        if (context.effectiveBoatCapability() != null) {
            return context.effectiveBoatCapability().cruiseSpeedKmh();
        }
        return context.properties().getTravel().getDefaultBoatKmh();
    }

    private static boolean samePoint(Point left, Point right) {
        if (left == null || right == null) {
            return false;
        }
        return Double.doubleToLongBits(left.getX()) == Double.doubleToLongBits(right.getX())
                && Double.doubleToLongBits(left.getY()) == Double.doubleToLongBits(right.getY());
    }
}
