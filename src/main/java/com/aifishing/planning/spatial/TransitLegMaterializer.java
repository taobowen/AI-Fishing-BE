package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists water-safe transit LineStrings after RoutePlanner has finalized the schedule.
 * Does not overwrite planned travel minutes or arrival/departure times.
 *
 * <p>Navigation report: A* runs at most once per hop at generate (or one-shot at session start
 * if a READY snapshot exists). GPS ingest performs 0 A* calls, 0 raster rebuilds, and 0 nav-edge
 * writes. Device ETA uses remaining LineString length; this class never reschedules the Plan.
 */
@Component
public class TransitLegMaterializer {

    private static final Logger log = LoggerFactory.getLogger(TransitLegMaterializer.class);

    private final SnapshotWaterPathService waterPathService;
    private final SpatialSnapshotService spatialSnapshotService;
    private final PlanningProperties properties;

    public TransitLegMaterializer(
            SnapshotWaterPathService waterPathService,
            SpatialSnapshotService spatialSnapshotService,
            PlanningProperties properties
    ) {
        this.waterPathService = waterPathService;
        this.spatialSnapshotService = spatialSnapshotService;
        this.properties = properties;
    }

    public List<TripPlanTransitLeg> materialize(RoutePlanner.RouteResult route, PlanningContext context) {
        GenerateProfiler.current().start(GenerateProfiler.TRANSIT_MATERIALIZATION);
        try {
            return materializeInner(route, context);
        } finally {
            GenerateProfiler.current().end(GenerateProfiler.TRANSIT_MATERIALIZATION);
        }
    }

    private List<TripPlanTransitLeg> materializeInner(RoutePlanner.RouteResult route, PlanningContext context) {
        if (route == null || route.stops() == null || route.stops().isEmpty()) {
            return List.of();
        }
        SpatialSnapshotView view = context == null ? null : context.spatialSnapshot();
        Point launch = context == null ? null : context.routeStartPoint();
        UUID accessPointId = launchAccessId(context);
        String launchKey = TransitPathKeys.launch(accessPointId);
        String navigationVersion = navigationVersion(view);
        List<TripPlanTransitLeg> legs = new ArrayList<>();
        List<PlannedStop> stops = route.stops();
        int sequence = 1;
        PlannedStop first = stops.getFirst();
        legs.add(build(
                sequence++,
                TransitEndpointKind.LAUNCH,
                TransitEndpointKind.VISIT,
                null,
                first.visitId(),
                launch,
                first.entryPoint(),
                launchKey,
                TransitPathKeys.visitEntry(first.visitId()),
                plannedMinutes(first.fromPrevious()),
                view,
                navigationVersion
        ));
        for (int i = 1; i < stops.size(); i++) {
            PlannedStop previous = stops.get(i - 1);
            PlannedStop current = stops.get(i);
            legs.add(build(
                    sequence++,
                    TransitEndpointKind.VISIT,
                    TransitEndpointKind.VISIT,
                    previous.visitId(),
                    current.visitId(),
                    previous.exitPoint(),
                    current.entryPoint(),
                    TransitPathKeys.visitExit(previous.visitId()),
                    TransitPathKeys.visitEntry(current.visitId()),
                    plannedMinutes(current.fromPrevious()),
                    view,
                    navigationVersion
            ));
        }
        PlannedStop last = stops.getLast();
        legs.add(build(
                sequence,
                TransitEndpointKind.VISIT,
                TransitEndpointKind.RETURN,
                last.visitId(),
                null,
                last.exitPoint(),
                launch,
                TransitPathKeys.visitExit(last.visitId()),
                launchKey,
                plannedMinutes(route.returnTravel()),
                view,
                navigationVersion
        ));
        return legs;
    }

    public List<TripPlanTransitLeg> materializeExistingPlan(TripPlan plan, List<TripWaypoint> waypoints) {
        if (plan == null || waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        SpatialSnapshotView view = loadReadySnapshot(waypoints);
        Point launch = launchPoint(plan);
        UUID accessPointId = accessPointId(plan);
        String launchKey = TransitPathKeys.launch(accessPointId);
        String navigationVersion = navigationVersion(view);
        List<TripPlanTransitLeg> legs = new ArrayList<>();
        int sequence = 1;
        TripWaypoint first = waypoints.getFirst();
        legs.add(build(
                sequence++,
                TransitEndpointKind.LAUNCH,
                TransitEndpointKind.VISIT,
                null,
                visitId(first),
                launch,
                first.resolvedEntryPoint(),
                launchKey,
                TransitPathKeys.visitEntry(visitId(first)),
                minutes(first.getEstimatedTravelMinutesFromPrevious()),
                view,
                navigationVersion
        ));
        for (int i = 1; i < waypoints.size(); i++) {
            TripWaypoint previous = waypoints.get(i - 1);
            TripWaypoint current = waypoints.get(i);
            legs.add(build(
                    sequence++,
                    TransitEndpointKind.VISIT,
                    TransitEndpointKind.VISIT,
                    visitId(previous),
                    visitId(current),
                    previous.resolvedExitPoint(),
                    current.resolvedEntryPoint(),
                    TransitPathKeys.visitExit(visitId(previous)),
                    TransitPathKeys.visitEntry(visitId(current)),
                    minutes(current.getEstimatedTravelMinutesFromPrevious()),
                    view,
                    navigationVersion
            ));
        }
        TripWaypoint last = waypoints.getLast();
        BigDecimal returnMinutes = returnMinutes(plan, waypoints);
        legs.add(build(
                sequence,
                TransitEndpointKind.VISIT,
                TransitEndpointKind.RETURN,
                visitId(last),
                null,
                last.resolvedExitPoint(),
                launch,
                TransitPathKeys.visitExit(visitId(last)),
                launchKey,
                returnMinutes,
                view,
                navigationVersion
        ));
        return legs;
    }

    private TripPlanTransitLeg build(
            int sequence,
            TransitEndpointKind fromKind,
            TransitEndpointKind toKind,
            UUID fromVisitId,
            UUID toVisitId,
            Point from,
            Point to,
            String fromKey,
            String toKey,
            BigDecimal plannedTravelMinutes,
            SpatialSnapshotView view,
            String navigationVersion
    ) {
        TripPlanTransitLeg leg = new TripPlanTransitLeg();
        leg.setSequence(sequence);
        leg.setFromKind(fromKind);
        leg.setToKind(toKind);
        leg.setFromVisitId(fromVisitId);
        leg.setToVisitId(toVisitId);
        leg.setPlannedTravelMinutes(plannedTravelMinutes);
        leg.setNavigationVersion(navigationVersion);
        if (view == null || from == null || to == null) {
            return leg;
        }
        try {
            Optional<LocalWaterPathEstimator.PathEstimate> path = waterPathService.transitPath(
                    view, fromKey, toKey, from, to, properties.getSpatial());
            if (path.isPresent()) {
                LocalWaterPathEstimator.PathEstimate estimate = path.get();
                leg.setTransitPath(estimate.path());
                leg.setPathDistanceMeters(BigDecimal.valueOf(estimate.meters()).setScale(2, RoundingMode.HALF_UP));
                waterPathService.findTransitPathId(view.id(), fromKey, toKey)
                        .ifPresent(leg::setSourceWaterPathId);
            }
        } catch (RuntimeException ex) {
            log.warn("Transit path materialization failed for {} -> {}: {}", fromKey, toKey, ex.getMessage());
        }
        return leg;
    }

    private SpatialSnapshotView loadReadySnapshot(List<TripWaypoint> waypoints) {
        UUID snapshotId = waypoints.stream()
                .map(TripWaypoint::getSpatialPlanningSnapshotId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (snapshotId == null) {
            return null;
        }
        try {
            return spatialSnapshotService.load(snapshotId);
        } catch (RuntimeException ex) {
            log.info("One-shot transit materialize skipped: {}", ex.getMessage());
            return null;
        }
    }

    private static UUID launchAccessId(PlanningContext context) {
        if (context == null) {
            return null;
        }
        if (context.access() != null && context.access().accessPointId() != null) {
            return context.access().accessPointId();
        }
        if (context.launch() != null) {
            return context.launch().officialAccessPointId();
        }
        return null;
    }

    private static String navigationVersion(SpatialSnapshotView view) {
        if (view != null && view.snapshot() != null && view.snapshot().getNavigationVersion() != null) {
            return view.snapshot().getNavigationVersion();
        }
        return null;
    }

    private static BigDecimal plannedMinutes(TravelEstimate estimate) {
        if (estimate == null || estimate.unknownTravel()) {
            return null;
        }
        return BigDecimal.valueOf(estimate.minutes()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal minutes(BigDecimal value) {
        return value;
    }

    private static BigDecimal returnMinutes(TripPlan plan, List<TripWaypoint> waypoints) {
        if (plan.getTotalEstimatedTravelMinutes() == null) {
            return null;
        }
        BigDecimal hops = waypoints.stream()
                .map(TripWaypoint::getEstimatedTravelMinutesFromPrevious)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = plan.getTotalEstimatedTravelMinutes().subtract(hops);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    private static UUID visitId(TripWaypoint waypoint) {
        Map<String, Object> metadata = waypoint.getMetadata();
        if (metadata != null && metadata.get("visitId") != null) {
            try {
                return UUID.fromString(String.valueOf(metadata.get("visitId")));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return waypoint.getVisitScopeId() != null ? waypoint.getVisitScopeId() : waypoint.getId();
    }

    @SuppressWarnings("unchecked")
    private static Point launchPoint(TripPlan plan) {
        Map<String, Object> metadata = plan.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object launch = metadata.get("launchSelection");
        if (launch instanceof Map<?, ?> map) {
            Object routeStart = map.get("routeStartPoint");
            if (routeStart instanceof Map<?, ?> point) {
                return toPoint(point.get("lat"), point.get("lng"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static UUID accessPointId(TripPlan plan) {
        Map<String, Object> metadata = plan.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object id = metadata.get("accessPointId");
        if (id != null) {
            try {
                return UUID.fromString(String.valueOf(id));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Object launch = metadata.get("launchSelection");
        if (launch instanceof Map<?, ?> map && map.get("officialAccessPointId") != null) {
            try {
                return UUID.fromString(String.valueOf(map.get("officialAccessPointId")));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Point toPoint(Object lat, Object lng) {
        if (lat == null || lng == null) {
            return null;
        }
        try {
            org.locationtech.jts.geom.GeometryFactory factory =
                    new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);
            Point point = factory.createPoint(new org.locationtech.jts.geom.Coordinate(
                    Double.parseDouble(String.valueOf(lng)),
                    Double.parseDouble(String.valueOf(lat))
            ));
            point.setSRID(4326);
            return point;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
