package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GetAlternativeRouteParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.LakeNavRaster;
import com.aifishing.planning.spatial.LocalWaterPathEstimator;
import com.aifishing.planning.spatial.SnapshotWaterPathService;
import com.aifishing.planning.spatial.SpatialSnapshotService;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.planning.spatial.repo.TripPlanTransitLegRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;

/**
 * Reuses existing water-path / transit. Does not invent a straight-line route.
 * Paths longer than {@code remainingRangeMeters} are treated as insufficient ({@code UNKNOWN}).
 */
@Component
public class GetAlternativeRouteTool implements AgentTool {

    private final TripWaypointRepository tripWaypointRepository;
    private final TripPlanTransitLegRepository transitLegRepository;
    private final SpatialSnapshotService spatialSnapshotService;
    private final SnapshotWaterPathService waterPathService;
    private final PlanningProperties planningProperties;
    private final Clock clock;

    public GetAlternativeRouteTool(
            TripWaypointRepository tripWaypointRepository,
            TripPlanTransitLegRepository transitLegRepository,
            SpatialSnapshotService spatialSnapshotService,
            SnapshotWaterPathService waterPathService,
            PlanningProperties planningProperties,
            Clock clock
    ) {
        this.tripWaypointRepository = tripWaypointRepository;
        this.transitLegRepository = transitLegRepository;
        this.spatialSnapshotService = spatialSnapshotService;
        this.waterPathService = waterPathService;
        this.planningProperties = planningProperties;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_ALTERNATIVE_ROUTE;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetAlternativeRouteParams> parsed = AgentToolSupport.parse(
                request, name(), "GetAlternativeRouteParams", GetAlternativeRouteParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            GetAlternativeRouteParams params = parsed.params();
            TripWaypoint destination = tripWaypointRepository.findById(params.toTripWaypointId()).orElse(null);
            if (destination == null || destination.getLocation() == null) {
                return AgentToolSupport.unknown(name(), clock);
            }
            Optional<Route> route = waterPath(params, destination)
                    .or(() -> existingTransit(destination));
            if (route.isEmpty()) {
                return AgentToolSupport.unknown(name(), clock);
            }
            Route found = route.get();
            if (params.remainingRangeMeters() != null && found.distanceMeters() > params.remainingRangeMeters()) {
                return AgentToolSupport.unknown(name(), clock);
            }
            ObjectNode data = GuidanceContracts.mapper().createObjectNode();
            data.put("toTripWaypointId", destination.getId().toString());
            data.put("distanceMeters", found.distanceMeters());
            if (found.estimatedMinutes() != null) {
                data.put("estimatedMinutes", found.estimatedMinutes());
            }
            data.put("routeSource", found.source());
            if (params.remainingRangeMeters() != null) {
                data.put("remainingRangeMeters", params.remainingRangeMeters());
                data.put("withinRemainingRange", true);
            }
            return AgentToolSupport.ok(name(), clock, data);
        } catch (RuntimeException ex) {
            return AgentToolSupport.error(name(), clock);
        }
    }

    private Optional<Route> waterPath(GetAlternativeRouteParams params, TripWaypoint destination) {
        if (destination.getSpatialPlanningSnapshotId() == null) {
            return Optional.empty();
        }
        try {
            SpatialSnapshotView view = spatialSnapshotService.load(destination.getSpatialPlanningSnapshotId());
            if (view == null || view.raster() == null) {
                return Optional.empty();
            }
            Point from = AgentToolSupport.point(params.fromLatitudeWgs84(), params.fromLongitudeWgs84());
            Point to = destination.getLocation();
            String fromKey = LakeNavRaster.cellKey(view.raster().cellOf(from));
            String toKey = LakeNavRaster.cellKey(view.raster().cellOf(to));
            if (fromKey.isBlank() || toKey.isBlank()) {
                return Optional.empty();
            }
            Optional<LocalWaterPathEstimator.PathEstimate> estimate = waterPathService.transitPath(
                    view, fromKey, toKey, from, to, planningProperties.getSpatial());
            return estimate.map(path -> new Route(path.meters(), path.minutes(), "water-path"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<Route> existingTransit(TripWaypoint destination) {
        return transitLegRepository.findByTripPlanIdOrderBySequenceAsc(destination.getTripPlanId()).stream()
                .filter(leg -> destination.getId().equals(leg.getToVisitId()))
                .filter(leg -> leg.getPathDistanceMeters() != null)
                .min(Comparator.comparingDouble(leg -> leg.getPathDistanceMeters().doubleValue()))
                .map(GetAlternativeRouteTool::fromLeg);
    }

    private static Route fromLeg(TripPlanTransitLeg leg) {
        Double minutes = leg.getPlannedTravelMinutes() == null
                ? null
                : leg.getPlannedTravelMinutes().doubleValue();
        return new Route(leg.getPathDistanceMeters().doubleValue(), minutes, "transit-leg");
    }

    private record Route(double distanceMeters, Double estimatedMinutes, String source) {
    }
}
