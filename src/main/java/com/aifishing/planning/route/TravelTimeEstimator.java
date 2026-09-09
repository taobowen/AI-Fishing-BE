package com.aifishing.planning.route;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

@Component
public class TravelTimeEstimator {

    public TravelEstimate estimate(Point from, Point to, PlanningContext context) {
        if (from == null || to == null) {
            return TravelEstimate.unspecified();
        }
        double geodesicM = GeoMetrics.distanceM(from, to);
        boolean landCrossing = context.geometry() != null && context.geometry().landCrossing(from, to);
        PlanningProperties.Travel travel = context.properties().getTravel();
        double factor = landCrossing ? travel.getLandCrossingDetourFactor() : travel.getDetourFactor();
        double speedKmh = speedKmh(context);
        double minutes = geodesicM * factor / (speedKmh * 1000.0 / 60.0);
        return new TravelEstimate(geodesicM, minutes, factor, landCrossing);
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
        double geodesicM = GeoMetrics.distanceM(from, to);
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
}
