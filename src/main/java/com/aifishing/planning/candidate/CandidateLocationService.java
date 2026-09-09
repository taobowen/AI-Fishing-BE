package com.aifishing.planning.candidate;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.algorithm.InteriorPointArea;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CandidateLocationService {

    static final double LAKEWARD_OFFSET_M = 15;
    static final double ISLAND_EDGE_BUFFER_M = 40;

    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public Optional<Point> locate(LakeFeature feature, LakePlanningGeometry lake) {
        if (feature == null || feature.getGeometry() == null || feature.getGeometry().isEmpty()) {
            return Optional.empty();
        }
        Geometry geometry = feature.getGeometry();
        FeatureType type = feature.getType();
        Point candidate = switch (type) {
            case HUMP, FLAT, BASIN -> arealInterior(geometry);
            case DROP_OFF -> dropOffPoint(geometry, lake);
            case POINT -> pointOrCentroid(geometry);
            case ISLAND_EDGE -> islandEdgePoint(geometry, lake);
        };
        if (candidate != null && lake.validFishingPoint(candidate)) {
            return Optional.of(candidate);
        }
        return tryOffsets(candidate != null ? candidate : pointOrCentroid(geometry), lake);
    }

    public Optional<Point> nearestValidFishingPoint(Point origin, LakePlanningGeometry lake) {
        return tryOffsets(origin, lake);
    }

    private Point arealInterior(Geometry geometry) {
        if (geometry.getDimension() >= 2) {
            Coordinate interior = new InteriorPointArea(geometry).getInteriorPoint();
            return point(interior);
        }
        return pointOrCentroid(geometry);
    }

    private Point dropOffPoint(Geometry geometry, LakePlanningGeometry lake) {
        LineString longest = longestLine(geometry);
        if (longest == null) {
            return pointOrCentroid(geometry);
        }
        Coordinate mid = midpoint(longest);
        Point midPoint = point(mid);
        if (lake.validFishingPoint(midPoint) && geometry.getDimension() < 2) {
            Point offset = lakewardOffset(longest, mid, lake);
            if (offset != null) {
                return offset;
            }
        }
        Point offset = lakewardOffset(longest, mid, lake);
        return offset != null ? offset : midPoint;
    }

    private Point islandEdgePoint(Geometry geometry, LakePlanningGeometry lake) {
        if (!lake.hasWater()) {
            return pointOrCentroid(geometry);
        }
        double lat = GeoMetrics.referenceLat(geometry);
        Geometry buffered = geometry.buffer(GeoMetrics.bufferDegrees(ISLAND_EDGE_BUFFER_M, lat));
        Geometry search = lake.water().intersection(buffered);
        if (search == null || search.isEmpty()) {
            search = lake.water().intersection(
                    geometry.buffer(GeoMetrics.bufferDegrees(ISLAND_EDGE_BUFFER_M * 2, lat)));
        }
        if (search != null && !search.isEmpty() && search.getDimension() >= 2) {
            for (Geometry island : lake.islands()) {
                if (island != null && !island.isEmpty()) {
                    try {
                        search = search.difference(island);
                    } catch (Exception ignored) {
                        // keep previous search
                    }
                }
            }
        }
        if (search != null && !search.isEmpty() && search.getDimension() >= 2) {
            return point(new InteriorPointArea(search).getInteriorPoint());
        }
        return arealInterior(geometry);
    }

    private Optional<Point> tryOffsets(Point origin, LakePlanningGeometry lake) {
        if (origin == null) {
            return Optional.empty();
        }
        if (origin.isEmpty()) {
            return Optional.empty();
        }
        if (lake.validFishingPoint(origin)) {
            return Optional.of(origin);
        }
        double lat = origin.getY();
        double[] meters = {10, 15, 25, 40, 60, 80};
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (double meter : meters) {
            for (double[] dir : dirs) {
                double lng = origin.getX() + (dir[0] * meter) / GeoMetrics.metersPerDegreeLng(lat);
                double lat2 = origin.getY() + (dir[1] * meter) / GeoMetrics.metersPerDegreeLat();
                Point shifted = point(new Coordinate(lng, lat2));
                if (lake.validFishingPoint(shifted)) {
                    return Optional.of(shifted);
                }
            }
        }
        return Optional.empty();
    }

    private Point lakewardOffset(LineString line, Coordinate mid, LakePlanningGeometry lake) {
        Coordinate[] coordinates = line.getCoordinates();
        if (coordinates.length < 2) {
            return null;
        }
        Coordinate a = coordinates[0];
        Coordinate b = coordinates[coordinates.length - 1];
        for (int i = 0; i < coordinates.length - 1; i++) {
            if (segmentContains(coordinates[i], coordinates[i + 1], mid)) {
                a = coordinates[i];
                b = coordinates[i + 1];
                break;
            }
        }
        double lat = mid.y;
        double dxM = (b.x - a.x) * GeoMetrics.metersPerDegreeLng(lat);
        double dyM = (b.y - a.y) * GeoMetrics.metersPerDegreeLat();
        double length = Math.hypot(dxM, dyM);
        if (length == 0) {
            return null;
        }
        double ux = -dyM / length;
        double uy = dxM / length;
        Point first = offsetMeters(mid, ux, uy, LAKEWARD_OFFSET_M);
        Point second = offsetMeters(mid, -ux, -uy, LAKEWARD_OFFSET_M);
        if (lake.validFishingPoint(first)) {
            return first;
        }
        if (lake.validFishingPoint(second)) {
            return second;
        }
        return null;
    }

    private Point offsetMeters(Coordinate origin, double ux, double uy, double meters) {
        double lat = origin.y;
        double lng = origin.x + (ux * meters) / GeoMetrics.metersPerDegreeLng(lat);
        double lat2 = origin.y + (uy * meters) / GeoMetrics.metersPerDegreeLat();
        return point(new Coordinate(lng, lat2));
    }

    private boolean segmentContains(Coordinate a, Coordinate b, Coordinate mid) {
        double minX = Math.min(a.x, b.x);
        double maxX = Math.max(a.x, b.x);
        double minY = Math.min(a.y, b.y);
        double maxY = Math.max(a.y, b.y);
        return mid.x >= minX - 1e-12 && mid.x <= maxX + 1e-12 && mid.y >= minY - 1e-12 && mid.y <= maxY + 1e-12;
    }

    private LineString longestLine(Geometry geometry) {
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        LineString longest = null;
        double best = -1;
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof LineString lineString) {
                double length = GeoMetrics.lengthM(lineString);
                if (length > best) {
                    best = length;
                    longest = lineString;
                }
            } else if (part.getBoundary() instanceof LineString boundary) {
                double length = GeoMetrics.lengthM(boundary);
                if (length > best) {
                    best = length;
                    longest = boundary;
                }
            }
        }
        if (longest == null && geometry.getBoundary() instanceof LineString boundary) {
            return boundary;
        }
        return longest;
    }

    private Coordinate midpoint(LineString line) {
        Coordinate[] coordinates = line.getCoordinates();
        if (coordinates.length < 2) {
            return line.getCentroid().getCoordinate();
        }
        int best = 0;
        double bestLen = -1;
        for (int i = 0; i < coordinates.length - 1; i++) {
            double len = GeoMetrics.distanceM(coordinates[i], coordinates[i + 1], coordinates[i].y);
            if (len > bestLen) {
                bestLen = len;
                best = i;
            }
        }
        Coordinate a = coordinates[best];
        Coordinate b = coordinates[best + 1];
        return new Coordinate((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
    }

    private Point pointOrCentroid(Geometry geometry) {
        if (geometry instanceof Point point) {
            Point copy = factory.createPoint(point.getCoordinate());
            copy.setSRID(GeoMapper.SRID);
            return copy;
        }
        Point centroid = geometry.getCentroid();
        centroid.setSRID(GeoMapper.SRID);
        return centroid;
    }

    private Point point(Coordinate coordinate) {
        Point point = factory.createPoint(coordinate);
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
