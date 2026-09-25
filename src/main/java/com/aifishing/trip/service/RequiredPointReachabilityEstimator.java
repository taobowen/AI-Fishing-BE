package com.aifishing.trip.service;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.domain.Lake;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.trip.api.RequiredPointReachabilityResponse;
import com.aifishing.trip.domain.Trip;
import org.locationtech.jts.geom.Point;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fast Required Point reachability envelope. Uses
 * {@link BoatCapabilityFilter#travelCapKm(PlanningContext)} with no Beam or A*.
 */
public final class RequiredPointReachabilityEstimator {

    public static final String DISCLAIMER =
            "Early estimate only. Final Generate may still reject a point for weather, safety, time, or routing.";

    private RequiredPointReachabilityEstimator() {
    }

    public static RequiredPointReachabilityResponse estimate(
            FishingMode fishingMode,
            GeoPointDto routeStartDto,
            Point routeStartPoint,
            Boat boat,
            LocalTime fishingStartTime,
            LocalTime fishingEndTime,
            PlanningProperties properties
    ) {
        if (fishingMode != FishingMode.BOAT || routeStartPoint == null || boat == null) {
            return skip(routeStartDto);
        }
        PlanningContext context = minimalContext(
                fishingMode,
                routeStartPoint,
                boat,
                fishingStartTime,
                fishingEndTime,
                properties == null ? new PlanningProperties() : properties
        );
        double capKm = BoatCapabilityFilter.travelCapKm(context);
        if (!Double.isFinite(capKm) || capKm <= 0) {
            return skip(routeStartDto);
        }
        return new RequiredPointReachabilityResponse(routeStartDto, capKm, true, DISCLAIMER);
    }

    public static RequiredPointReachabilityResponse skip(GeoPointDto routeStart) {
        return new RequiredPointReachabilityResponse(routeStart, null, false, DISCLAIMER);
    }

    private static PlanningContext minimalContext(
            FishingMode fishingMode,
            Point routeStart,
            Boat boat,
            LocalTime fishingStartTime,
            LocalTime fishingEndTime,
            PlanningProperties properties
    ) {
        Trip trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setUserId(UUID.randomUUID());
        trip.setLakeId(UUID.randomUUID());
        trip.setFishingMode(fishingMode);
        trip.setFishingStartTime(fishingStartTime);
        trip.setFishingEndTime(fishingEndTime);
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        AccessResolution access = new AccessResolution(
                AccessResolution.AccessStatus.SELECTED,
                null,
                null,
                routeStart
        );
        return new PlanningContext(
                trip,
                lake,
                boat,
                access,
                null,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                properties,
                new ArrayList<>()
        );
    }
}
