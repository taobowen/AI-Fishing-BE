package com.aifishing.launch;

import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.launch.api.CustomLaunchPreviewResponse;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Snaps a requested point to the selected lake shoreline and derives a water-side
 * {@code routeStartPoint} using meter-accurate projected geometry. Never uses a lake centroid.
 */
@Component
public class CustomLaunchResolver {

    public static final String TOO_FAR = "CUSTOM_LAUNCH_TOO_FAR_FROM_SHORE";
    public static final String ANCHOR_UNAVAILABLE = "CUSTOM_LAUNCH_ROUTE_ANCHOR_UNAVAILABLE";
    public static final String MISSING_WATER = "CUSTOM_LAUNCH_MISSING_WATER";
    public static final String ISLAND_SHORE = "CUSTOM_LAUNCH_ISLAND_SHORE";

    private static final Logger log = LoggerFactory.getLogger(CustomLaunchResolver.class);
    private static final double RING_HIT_METERS = 0.75;

    private final LocalMetricCrs localMetricCrs;
    private final LaunchProperties properties;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public CustomLaunchResolver(LocalMetricCrs localMetricCrs, LaunchProperties properties) {
        this.localMetricCrs = localMetricCrs;
        this.properties = properties;
    }

    public Optional<Resolution> resolve(Point requestedWgs, LakePlanningGeometry geometry) {
        return resolve(requestedWgs, geometry, true);
    }

    public Optional<Resolution> resolve(Point requestedWgs, LakePlanningGeometry geometry, boolean enforceMaxSnap) {
        if (requestedWgs == null || geometry == null || !geometry.hasWater()) {
            return Optional.empty();
        }
        double lng = requestedWgs.getX();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(geometry.water(), lng);
        Point requested = projected.toMetricPoint(requestedWgs);
        Polygon component = selectComponent((Geometry) projected.geometry(), requested);
        if (component == null) {
            return Optional.empty();
        }
        Geometry shoreline = component.getBoundary();
        CoordinatePair nearest = nearest(shoreline, requested);
        double snapMeters = nearest.distance;
        boolean inWater = component.covers(requested) || component.contains(requested);
        if (enforceMaxSnap && !inWater && snapMeters > properties.getCustom().getMaxSnapMeters()) {
            log.debug("Custom launch snap rejected: distance {} m exceeds max", round(snapMeters));
            return Optional.empty();
        }
        Point shoreMetric = factory.createPoint(nearest.onTarget);
        shoreMetric.setSRID(projected.srid());
        ShorelineKind kind = shorelineKind(component, shoreMetric, geometry, projected);
        List<String> warnings = new ArrayList<>();
        if (kind == ShorelineKind.ISLAND_OR_INTERIOR_RING) {
            warnings.add(ISLAND_SHORE);
        }
        Point routeMetric = waterAnchor(component, shoreMetric);
        if (routeMetric == null) {
            log.debug("Custom launch water anchor unavailable after metric buffers");
            return Optional.empty();
        }
        Point shoreWgs = projected.toWgs84Point(asPoint(shoreMetric, projected.srid()));
        Point routeWgs = projected.toWgs84Point(asPoint(routeMetric, projected.srid()));
        log.info("Custom launch resolved snap={}m kind={} warnings={}",
                round(snapMeters), kind, warnings.size());
        log.debug("Custom launch points requested/shore/route resolved with snap {} m", round(snapMeters));
        return Optional.of(new Resolution(
                requestedWgs,
                shoreWgs,
                routeWgs,
                snapMeters,
                kind,
                properties.getCustom().getResolutionVersion(),
                List.copyOf(warnings),
                component
        ));
    }

    public FailureCode failureCode(Point requestedWgs, LakePlanningGeometry geometry) {
        return failureCode(requestedWgs, geometry, true);
    }

    public FailureCode failureCode(Point requestedWgs, LakePlanningGeometry geometry, boolean enforceMaxSnap) {
        if (requestedWgs == null || geometry == null || !geometry.hasWater()) {
            return FailureCode.MISSING_WATER;
        }
        double lng = requestedWgs.getX();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(geometry.water(), lng);
        Point requested = projected.toMetricPoint(requestedWgs);
        Polygon component = selectComponent((Geometry) projected.geometry(), requested);
        if (component == null) {
            return FailureCode.MISSING_WATER;
        }
        CoordinatePair nearest = nearest(component.getBoundary(), requested);
        boolean inWater = component.covers(requested) || component.contains(requested);
        if (enforceMaxSnap && !inWater && nearest.distance > properties.getCustom().getMaxSnapMeters()) {
            return FailureCode.TOO_FAR;
        }
        Point shoreMetric = factory.createPoint(nearest.onTarget);
        shoreMetric.setSRID(projected.srid());
        if (waterAnchor(component, shoreMetric) == null) {
            return FailureCode.ANCHOR_UNAVAILABLE;
        }
        return null;
    }

    public List<CustomLaunchPreviewResponse.NearbyOfficialSuggestion> nearbyOfficial(
            Point originWgs,
            List<LakeAccessPoint> launches,
            LakePlanningGeometry geometry
    ) {
        if (originWgs == null || launches == null || launches.isEmpty() || geometry == null || !geometry.hasWater()) {
            return List.of();
        }
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(geometry.water(), originWgs.getX());
        Point origin = projected.toMetricPoint(originWgs);
        double maxM = properties.getCustom().getNearbyOfficialMeters();
        List<CustomLaunchPreviewResponse.NearbyOfficialSuggestion> nearby = new ArrayList<>();
        for (LakeAccessPoint launch : launches) {
            if (launch.getLocation() == null) {
                continue;
            }
            Point metric = projected.toMetricPoint(launch.getLocation());
            double distance = origin.distance(metric);
            if (distance <= maxM) {
                nearby.add(new CustomLaunchPreviewResponse.NearbyOfficialSuggestion(
                        launch.getId(),
                        launch.getName(),
                        new com.aifishing.common.geo.GeoPointDto(launch.getLocation().getY(), launch.getLocation().getX()),
                        accessType(launch),
                        launch.getSource(),
                        com.aifishing.common.enums.LaunchVerification.AUTHORITATIVE,
                        round(distance)
                ));
            }
        }
        nearby.sort(Comparator.comparing(CustomLaunchPreviewResponse.NearbyOfficialSuggestion::distanceM));
        return nearby;
    }

    public boolean routeStartValid(Point routeStartWgs, LakePlanningGeometry geometry) {
        if (routeStartWgs == null || geometry == null || !geometry.hasWater()) {
            return false;
        }
        if (!geometry.inWater(routeStartWgs) || geometry.onIsland(routeStartWgs)) {
            return false;
        }
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(geometry.water(), routeStartWgs.getX());
        Point route = projected.toMetricPoint(routeStartWgs);
        Polygon component = selectComponent((Geometry) projected.geometry(), route);
        return component != null && (component.covers(route) || component.contains(route) || component.distance(route) < 0.5);
    }

    public double meterDistance(Point a, Point b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(a, a.getX());
        return projected.toMetricPoint(a).distance(projected.toMetricPoint(b));
    }

    public boolean materialMove(Point previous, Point next) {
        return meterDistance(previous, next) > properties.getCustom().getMaterialMoveMeters();
    }

    public static String accessType(LakeAccessPoint point) {
        if (point.getType() != null && !point.getType().isBlank()) {
            return point.getType();
        }
        return Boolean.TRUE.equals(point.getBoatLaunch()) ? "BOAT_LAUNCH" : "ACCESS";
    }

    private Point waterAnchor(Polygon component, Point shoreMetric) {
        List<Double> offsets = new ArrayList<>();
        offsets.add(properties.getCustom().getPreferredWaterOffsetMeters());
        offsets.addAll(properties.getCustom().getFallbackWaterOffsetMeters());
        for (Double offset : offsets) {
            if (offset == null || offset <= 0) {
                continue;
            }
            Geometry prepared = component.buffer(0);
            Geometry buffered = prepared.buffer(-offset);
            if (buffered == null || buffered.isEmpty()) {
                continue;
            }
            Geometry piece = nearestComponent(buffered, shoreMetric);
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            CoordinatePair nearest = nearest(piece, shoreMetric);
            Point route = factory.createPoint(nearest.onTarget);
            route.setSRID(component.getSRID());
            if (!route.isEmpty() && prepared.distance(route) < offset + 1.0) {
                return route;
            }
        }
        return null;
    }

    private ShorelineKind shorelineKind(
            Polygon component,
            Point shoreMetric,
            LakePlanningGeometry geometry,
            LocalMetricCrs.ProjectedGeometry projected
    ) {
        for (int i = 0; i < component.getNumInteriorRing(); i++) {
            LineString ring = component.getInteriorRingN(i);
            if (ring.distance(shoreMetric) <= RING_HIT_METERS) {
                return ShorelineKind.ISLAND_OR_INTERIOR_RING;
            }
        }
        Point shoreWgs = projected.toWgs84Point(asPoint(shoreMetric, projected.srid()));
        if (geometry.onIsland(shoreWgs)) {
            return ShorelineKind.ISLAND_OR_INTERIOR_RING;
        }
        return ShorelineKind.MAINLAND;
    }

    private static Polygon selectComponent(Geometry water, Point requested) {
        List<Polygon> polygons = polygons(water);
        if (polygons.isEmpty()) {
            return null;
        }
        for (Polygon polygon : polygons) {
            if (polygon.covers(requested) || polygon.contains(requested)) {
                return polygon;
            }
        }
        return polygons.stream()
                .min(Comparator.comparingDouble(polygon -> polygon.distance(requested)))
                .orElse(null);
    }

    private static Geometry nearestComponent(Geometry geometry, Point origin) {
        List<Polygon> polygons = polygons(geometry);
        if (polygons.isEmpty()) {
            return geometry;
        }
        return polygons.stream()
                .min(Comparator.comparingDouble(polygon -> polygon.distance(origin)))
                .map(polygon -> (Geometry) polygon)
                .orElse(geometry);
    }

    private static List<Polygon> polygons(Geometry geometry) {
        List<Polygon> polygons = new ArrayList<>();
        if (geometry instanceof Polygon polygon) {
            polygons.add(polygon);
            return polygons;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof Polygon polygon && !polygon.isEmpty()) {
                polygons.add(polygon);
            }
        }
        return polygons;
    }

    private static CoordinatePair nearest(Geometry target, Point origin) {
        org.locationtech.jts.geom.Coordinate[] pair = DistanceOp.nearestPoints(target, origin);
        return new CoordinatePair(pair[0], pair[1], origin.getCoordinate().distance(pair[0]));
    }

    private Point asPoint(Point point, int srid) {
        Point copy = factory.createPoint(point.getCoordinate());
        copy.setSRID(srid);
        return copy;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public enum FailureCode {
        TOO_FAR,
        ANCHOR_UNAVAILABLE,
        MISSING_WATER
    }

    public record Resolution(
            Point requestedPoint,
            Point shoreAccessPoint,
            Point routeStartPoint,
            double snapDistanceMeters,
            ShorelineKind shorelineKind,
            String resolutionVersion,
            List<String> warnings,
            Polygon metricComponent
    ) {
    }

    private record CoordinatePair(
            org.locationtech.jts.geom.Coordinate onTarget,
            org.locationtech.jts.geom.Coordinate onOrigin,
            double distance
    ) {
    }
}
