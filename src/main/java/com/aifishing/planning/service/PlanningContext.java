package com.aifishing.planning.service;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.boat.capability.ResolvedBoatCapability;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.GearType;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.launch.ResolvedTripLaunch;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.trip.domain.Trip;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record PlanningContext(
        Trip trip,
        Lake lake,
        Boat boat,
        AccessResolution access,
        LakePlanningGeometry geometry,
        List<FishingRestriction> restrictions,
        String regulationCoverageStatus,
        WeatherContext weather,
        List<GearType> gearTypes,
        FishingStrategyProfile profile,
        StrategyRun strategyRun,
        PlanningProperties properties,
        List<String> warnings,
        ResolvedBoatCapability baselineBoatCapability,
        EffectiveBoatCapability effectiveBoatCapability,
        ResolvedTripLaunch launch,
        com.aifishing.planning.spatial.SpatialSnapshotView spatialSnapshot
) {
    public PlanningContext {
        restrictions = restrictions == null ? List.of() : List.copyOf(restrictions);
        gearTypes = gearTypes == null ? List.of() : List.copyOf(gearTypes);
        warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public PlanningContext(
            Trip trip,
            Lake lake,
            Boat boat,
            AccessResolution access,
            LakePlanningGeometry geometry,
            List<FishingRestriction> restrictions,
            String regulationCoverageStatus,
            WeatherContext weather,
            List<GearType> gearTypes,
            FishingStrategyProfile profile,
            StrategyRun strategyRun,
            PlanningProperties properties,
            List<String> warnings
    ) {
        this(
                trip,
                lake,
                boat,
                access,
                geometry,
                restrictions,
                regulationCoverageStatus,
                weather,
                gearTypes,
                profile,
                strategyRun,
                properties,
                warnings,
                null,
                null,
                null,
                null
        );
    }

    public PlanningContext withLaunch(AccessResolution access, ResolvedTripLaunch launch) {
        return new PlanningContext(
                trip,
                lake,
                boat,
                access,
                geometry,
                restrictions,
                regulationCoverageStatus,
                weather,
                gearTypes,
                profile,
                strategyRun,
                properties,
                warnings,
                baselineBoatCapability,
                effectiveBoatCapability,
                launch,
                spatialSnapshot
        );
    }

    public PlanningContext withSnapshot(com.aifishing.planning.spatial.SpatialSnapshotView spatialSnapshot) {
        return new PlanningContext(
                trip,
                lake,
                boat,
                access,
                geometry,
                restrictions,
                regulationCoverageStatus,
                weather,
                gearTypes,
                profile,
                strategyRun,
                properties,
                warnings,
                baselineBoatCapability,
                effectiveBoatCapability,
                launch,
                spatialSnapshot
        );
    }

    public LocalDate tripDate() {
        return trip.getPlannedDate();
    }

    public FishingMode fishingMode() {
        return trip.getFishingMode();
    }

    public FishSpecies primarySpecies() {
        return trip.getPrimaryTargetSpecies();
    }

    public List<FishSpecies> secondarySpecies() {
        return trip.getSecondaryTargetSpecies() == null ? List.of() : trip.getSecondaryTargetSpecies();
    }

    public Point routeStartPoint() {
        if (launch != null && launch.routeStartPoint() != null) {
            return launch.routeStartPoint();
        }
        return access != null ? access.location() : null;
    }

    public boolean accessKnown() {
        return routeStartPoint() != null;
    }
}
