package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.processing.ProcessingProperties;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class ContourTopology {

    private final ProcessingProperties properties;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public ContourTopology(ProcessingProperties properties) {
        this.properties = properties;
    }

    public List<ClosedContour> closedPolygons(List<BathymetryContour> contours) {
        List<ClosedContour> closed = new ArrayList<>();
        for (BathymetryContour contour : contours) {
            if (contour.getGeometry() == null || contour.getDepthM() == null) {
                continue;
            }
            double depth = contour.getDepthM().doubleValue();
            for (LineString line : mergeLines(contour.getGeometry())) {
                Polygon polygon = toClosedPolygon(line);
                if (polygon == null || !polygon.isValid() || polygon.isEmpty()) {
                    continue;
                }
                polygon.setSRID(GeoMapper.SRID);
                double area = GeoMetrics.areaM2(polygon);
                if (area < properties.getMinClosedAreaM2()) {
                    continue;
                }
                closed.add(new ClosedContour(polygon, depth, contour.getSourceRecordId(), area));
            }
        }
        closed.sort(Comparator.comparingDouble(ClosedContour::areaM2).reversed());
        return closed;
    }

    public List<LineString> allLines(List<BathymetryContour> contours) {
        List<LineString> lines = new ArrayList<>();
        for (BathymetryContour contour : contours) {
            if (contour.getGeometry() == null) {
                continue;
            }
            lines.addAll(mergeLines(contour.getGeometry()));
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private List<LineString> mergeLines(MultiLineString multiLineString) {
        LineMerger merger = new LineMerger();
        merger.add(multiLineString);
        Collection<LineString> merged = merger.getMergedLineStrings();
        List<LineString> result = new ArrayList<>();
        if (merged == null || merged.isEmpty()) {
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                result.add((LineString) multiLineString.getGeometryN(i));
            }
            return result;
        }
        result.addAll(merged);
        return result;
    }

    private Polygon toClosedPolygon(LineString line) {
        Coordinate[] coordinates = line.getCoordinates();
        if (coordinates.length < 3) {
            return null;
        }
        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];
        double lat = first.y;
        boolean closed = first.equals2D(last)
                || GeoMetrics.distanceM(first, last, lat) <= properties.getCloseContourToleranceM();
        if (!closed) {
            return null;
        }
        Coordinate[] ring = coordinates;
        if (!first.equals2D(last)) {
            ring = new Coordinate[coordinates.length + 1];
            System.arraycopy(coordinates, 0, ring, 0, coordinates.length);
            ring[coordinates.length] = new Coordinate(first);
        }
        if (ring.length < 4) {
            return null;
        }
        LinearRing linearRing = factory.createLinearRing(ring);
        return factory.createPolygon(linearRing);
    }

    public ClosedContour parentOf(ClosedContour child, List<ClosedContour> all) {
        ClosedContour parent = null;
        for (ClosedContour candidate : all) {
            if (candidate == child) {
                continue;
            }
            if (candidate.polygon().contains(child.polygon())
                    && (parent == null || parent.polygon().contains(candidate.polygon()))) {
                parent = candidate;
            }
        }
        return parent;
    }

    public Double meanNearestSpacingM(List<BathymetryContour> contours) {
        List<LineString> lines = allLines(contours);
        if (lines.size() < 2) {
            return null;
        }
        List<Double> nearest = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            double min = Double.POSITIVE_INFINITY;
            for (int j = 0; j < lines.size(); j++) {
                if (i == j) {
                    continue;
                }
                double distance = GeoMetrics.distanceM(lines.get(i), lines.get(j));
                if (distance > 0 && distance < min) {
                    min = distance;
                }
            }
            if (Double.isFinite(min)) {
                nearest.add(min);
            }
        }
        if (nearest.isEmpty()) {
            return null;
        }
        return nearest.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public record ClosedContour(Polygon polygon, double depthM, String sourceRecordId, double areaM2) {
        public Geometry geometry() {
            return polygon;
        }
    }
}
