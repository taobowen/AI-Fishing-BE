package com.aifishing.planning.environment;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.springframework.stereotype.Component;

/**
 * Derives water-facing / downslope aspects from lake water geometry.
 * Stored {@code LakeFeature.orientation} is along-feature tangent and is never used as facing.
 */
@Component
public class LocalOrientationService {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double OFFSET_M = 20;

    public LocalOrientation resolve(CandidateSpot spot, LakePlanningGeometry geometry) {
        Double raw = spot == null ? null : spot.getRawOrientationDeg();
        Double rawNorm = raw == null ? null : AzimuthConvention.normalize(raw);
        if (spot == null || spot.getLocation() == null || geometry == null || !geometry.hasWater()) {
            return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, "NO_GEOMETRY");
        }
        FeatureType type = spot.getType();
        if (type == FeatureType.POINT || type == FeatureType.ISLAND_EDGE) {
            LocalOrientation shore = shorelineFacing(spot.getLocation(), geometry, rawNorm, "SHORELINE_NORMAL");
            if (shore.confidence() != OrientationConfidence.UNKNOWN) {
                return shore;
            }
            return withRaw(shore, rawNorm);
        }
        if (type == FeatureType.DROP_OFF) {
            return dropOffFacing(spot, geometry, rawNorm);
        }
        LocalOrientation nearby = shorelineFacing(spot.getLocation(), geometry, rawNorm, "NEARBY_SHORE");
        if (nearby.confidence() == OrientationConfidence.HIGH) {
            return new LocalOrientation(
                    nearby.shorelineWaterFacingAspect(),
                    nearby.slopeAspect(),
                    rawNorm,
                    false,
                    OrientationConfidence.MEDIUM,
                    nearby.source()
            );
        }
        return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, "INTERIOR_UNKNOWN");
    }

    private LocalOrientation dropOffFacing(CandidateSpot spot, LakePlanningGeometry geometry, Double rawNorm) {
        Geometry source = spot.getSourceGeometry();
        LineString line = asLine(source);
        if (line == null || line.getNumPoints() < 2) {
            return LocalOrientation.unknown();
        }
        Coordinate start = line.getCoordinateN(0);
        Coordinate end = line.getCoordinateN(line.getNumPoints() - 1);
        double tangent = tangentAzimuth(start, end);
        Double facing = pickWaterFacing(spot.getLocation(), tangent, geometry);
        if (facing == null) {
            return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, "DROP_OFF_BOTH_SIDES_WATER");
        }
        return new LocalOrientation(null, facing, rawNorm, false, OrientationConfidence.MEDIUM, "DROP_OFF_WATER_SIDE");
    }

    private LocalOrientation shorelineFacing(Point location, LakePlanningGeometry geometry, Double rawNorm, String source) {
        Geometry boundary = geometry.water().getBoundary();
        if (boundary == null || boundary.isEmpty()) {
            return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, "NO_BOUNDARY");
        }
        Segment segment = nearestSegment(boundary, location);
        if (segment == null) {
            return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, "NO_SEGMENT");
        }
        double tangent = tangentAzimuth(segment.a(), segment.b());
        Point origin = FACTORY.createPoint(segment.nearest);
        origin.setSRID(4326);
        Double facing = pickWaterFacing(origin, tangent, geometry);
        if (facing == null) {
            return new LocalOrientation(null, null, rawNorm, false, OrientationConfidence.UNKNOWN, source);
        }
        double distM = GeoMetrics.distanceM(location, origin);
        OrientationConfidence confidence = distM <= 80 ? OrientationConfidence.HIGH : OrientationConfidence.MEDIUM;
        if (distM > 250) {
            confidence = OrientationConfidence.LOW;
        }
        return new LocalOrientation(facing, null, rawNorm, false, confidence, source);
    }

    private static LocalOrientation withRaw(LocalOrientation base, Double rawNorm) {
        return new LocalOrientation(
                base.shorelineWaterFacingAspect(),
                base.slopeAspect(),
                rawNorm,
                false,
                base.confidence(),
                base.source()
        );
    }

    private Double pickWaterFacing(Point origin, double tangentAzimuth, LakePlanningGeometry geometry) {
        double n1 = AzimuthConvention.normalize(tangentAzimuth + 90.0);
        double n2 = AzimuthConvention.normalize(tangentAzimuth - 90.0);
        Point p1 = offset(origin, n1, OFFSET_M);
        Point p2 = offset(origin, n2, OFFSET_M);
        boolean w1 = geometry.inWater(p1) && !geometry.onIsland(p1);
        boolean w2 = geometry.inWater(p2) && !geometry.onIsland(p2);
        if (w1 && !w2) {
            return n1;
        }
        if (w2 && !w1) {
            return n2;
        }
        return null;
    }

    private static Point offset(Point origin, double azimuthDeg, double meters) {
        double lat = origin.getY();
        double rad = Math.toRadians(azimuthDeg);
        double dLat = (meters * Math.cos(rad)) / GeoMetrics.metersPerDegreeLat();
        double dLng = (meters * Math.sin(rad)) / GeoMetrics.metersPerDegreeLng(lat);
        Point point = FACTORY.createPoint(new Coordinate(origin.getX() + dLng, origin.getY() + dLat));
        point.setSRID(4326);
        return point;
    }

    private static double tangentAzimuth(Coordinate a, Coordinate b) {
        double lat = (a.y + b.y) / 2.0;
        double dLat = (b.y - a.y) * GeoMetrics.metersPerDegreeLat();
        double dLng = (b.x - a.x) * GeoMetrics.metersPerDegreeLng(lat);
        return AzimuthConvention.normalize(Math.toDegrees(Math.atan2(dLng, dLat)));
    }

    private static LineString asLine(Geometry geometry) {
        if (geometry instanceof LineString line) {
            return line;
        }
        if (geometry != null && geometry.getNumGeometries() > 0 && geometry.getGeometryN(0) instanceof LineString line) {
            return line;
        }
        return null;
    }

    private Segment nearestSegment(Geometry boundary, Point location) {
        double best = Double.POSITIVE_INFINITY;
        Segment found = null;
        int parts = Math.max(1, boundary.getNumGeometries());
        for (int i = 0; i < parts; i++) {
            Geometry part = boundary.getNumGeometries() > 1 ? boundary.getGeometryN(i) : boundary;
            Coordinate[] coords = part.getCoordinates();
            for (int j = 0; j < coords.length - 1; j++) {
                LineString edge = FACTORY.createLineString(new Coordinate[]{coords[j], coords[j + 1]});
                double d = location.distance(edge);
                if (d < best) {
                    best = d;
                    Coordinate[] nearest = DistanceOp.nearestPoints(location, edge);
                    found = new Segment(coords[j], coords[j + 1], nearest[1]);
                }
            }
        }
        return found;
    }

    private record Segment(Coordinate a, Coordinate b, Coordinate nearest) {
    }
}
