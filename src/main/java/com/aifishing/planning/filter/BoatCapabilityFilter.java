package com.aifishing.planning.filter;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;

@Component
public class BoatCapabilityFilter implements CandidateFilter {

    @Override
    public FilterResult apply(CandidateSpot candidate, PlanningContext context) {
        if (context.fishingMode() != FishingMode.BOAT) {
            return FilterResult.accept();
        }
        if (candidate.getLocation() == null) {
            return FilterResult.reject(RejectionReason.INVALID_LOCATION);
        }
        if (!context.accessKnown()) {
            return FilterResult.accept();
        }
        double fromLaunchKm = GeoMetrics.distanceM(context.routeStartPoint(), candidate.getLocation()) / 1000.0;
        if (fromLaunchKm > travelCapKm(context)) {
            return FilterResult.reject(RejectionReason.BOAT_TRAVEL_UNREASONABLE);
        }
        return FilterResult.accept();
    }

    public static double travelCapKm(PlanningContext context) {
        if (context == null || context.fishingMode() != FishingMode.BOAT || !context.accessKnown()) {
            return Double.POSITIVE_INFINITY;
        }
        double durationCapKm = speedKmh(context)
                * tripHours(context)
                * context.properties().getTravel().getMaxOneWayFractionOfTrip();
        return Math.min(maxOneWayKm(context), durationCapKm);
    }

    private static double maxOneWayKm(PlanningContext context) {
        EffectiveBoatCapability effective = context.effectiveBoatCapability();
        if (effective != null && effective.rangeEnforced() && effective.effectiveUsableRangeKm() != null) {
            return Math.min(effective.maxLegKm(), effective.effectiveUsableRangeKm() / 2.0);
        }
        if (effective != null) {
            return effective.maxLegKm();
        }
        BoatType type = context.boat() == null ? BoatType.OTHER : context.boat().getType();
        return context.properties().maxOneWayKm(type);
    }

    private static double speedKmh(PlanningContext context) {
        if (context.effectiveBoatCapability() != null) {
            return context.effectiveBoatCapability().cruiseSpeedKmh();
        }
        return context.properties().getTravel().getDefaultBoatKmh();
    }

    private static double tripHours(PlanningContext context) {
        LocalTime start = context.trip().getFishingStartTime();
        LocalTime end = context.trip().getFishingEndTime();
        if (start == null || end == null || !end.isAfter(start)) {
            return 8;
        }
        return Math.max(0.5, Duration.between(start, end).toMinutes() / 60.0);
    }
}
