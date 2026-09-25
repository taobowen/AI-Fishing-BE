package com.aifishing.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class GeoMapper {

    public static final int SRID = 4326;
    /** ~9 m at mid-latitudes; enough for a map overlay without sending raw survey vertices. */
    public static final double MAP_LINE_SIMPLIFY_DEG = 0.00008;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    public Point toPoint(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }
        Point point = geometryFactory.createPoint(new Coordinate(dto.lng(), dto.lat()));
        point.setSRID(SRID);
        return point;
    }

    public GeoPointDto toDto(Point point) {
        if (point == null) {
            return null;
        }
        return new GeoPointDto(point.getY(), point.getX());
    }

    public MultiPolygon toMultiPolygon(GeoMultiPolygonDto dto) {
        if (dto == null || dto.polygons() == null || dto.polygons().isEmpty()) {
            return null;
        }
        Polygon[] polygons = dto.polygons().stream().map(this::toPolygon).toArray(Polygon[]::new);
        MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(polygons);
        multiPolygon.setSRID(SRID);
        return multiPolygon;
    }

    public GeoMultiPolygonDto toDto(MultiPolygon multiPolygon) {
        if (multiPolygon == null || multiPolygon.isEmpty()) {
            return null;
        }
        List<GeoPolygonDto> polygons = new ArrayList<>();
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            polygons.add(toDto((Polygon) multiPolygon.getGeometryN(i)));
        }
        return new GeoMultiPolygonDto(polygons);
    }

    private Polygon toPolygon(GeoPolygonDto dto) {
        LinearRing shell = geometryFactory.createLinearRing(closedRing(dto.ring()));
        LinearRing[] holes = (dto.holes() == null ? List.<List<GeoPointDto>>of() : dto.holes()).stream()
                .filter(hole -> hole != null && hole.size() >= 4)
                .map(hole -> geometryFactory.createLinearRing(closedRing(hole)))
                .toArray(LinearRing[]::new);
        Polygon polygon = geometryFactory.createPolygon(shell, holes);
        polygon.setSRID(SRID);
        return polygon;
    }

    private GeoPolygonDto toDto(Polygon polygon) {
        List<GeoPointDto> ring = ringDto(polygon.getExteriorRing().getCoordinates());
        List<List<GeoPointDto>> holes = new ArrayList<>();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            holes.add(ringDto(polygon.getInteriorRingN(i).getCoordinates()));
        }
        return new GeoPolygonDto(ring, holes);
    }

    private List<GeoPointDto> ringDto(Coordinate[] coordinates) {
        List<GeoPointDto> ring = new ArrayList<>();
        for (Coordinate coordinate : coordinates) {
            ring.add(new GeoPointDto(coordinate.getY(), coordinate.getX()));
        }
        return ring;
    }

    private Coordinate[] closedRing(List<GeoPointDto> ring) {
        Coordinate[] coordinates = ring.stream()
                .map(point -> new Coordinate(point.lng(), point.lat()))
                .toArray(Coordinate[]::new);
        if (coordinates.length == 0) {
            return coordinates;
        }
        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];
        if (!first.equals2D(last)) {
            coordinates = Arrays.copyOf(coordinates, coordinates.length + 1);
            coordinates[coordinates.length - 1] = new Coordinate(first);
        }
        return coordinates;
    }

    public GeoMultiLineStringDto toDto(MultiLineString multiLineString) {
        if (multiLineString == null || multiLineString.isEmpty()) {
            return null;
        }
        List<List<GeoPointDto>> lines = new ArrayList<>();
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            if (line == null || line.getNumPoints() < 2) {
                continue;
            }
            lines.add(ringDto(line.getCoordinates()));
        }
        return lines.isEmpty() ? null : new GeoMultiLineStringDto(lines);
    }

    public GeoMultiLineStringDto toMapDto(MultiLineString multiLineString) {
        if (multiLineString == null || multiLineString.isEmpty()) {
            return null;
        }
        Geometry simplified = DouglasPeuckerSimplifier.simplify(multiLineString, MAP_LINE_SIMPLIFY_DEG);
        return toDto(asMultiLineString(simplified));
    }

    private MultiLineString asMultiLineString(Geometry geometry) {
        if (geometry instanceof MultiLineString multiLineString && !multiLineString.isEmpty()) {
            multiLineString.setSRID(SRID);
            return multiLineString;
        }
        if (geometry instanceof LineString lineString && lineString.getNumPoints() >= 2) {
            MultiLineString multiLineString = geometryFactory.createMultiLineString(new LineString[]{lineString});
            multiLineString.setSRID(SRID);
            return multiLineString;
        }
        return null;
    }

    public org.locationtech.jts.geom.LineString toLineString(List<Point> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        Coordinate[] coordinates = points.stream()
                .filter(java.util.Objects::nonNull)
                .map(Point::getCoordinate)
                .toArray(Coordinate[]::new);
        if (coordinates.length < 2) {
            return null;
        }
        org.locationtech.jts.geom.LineString line = geometryFactory.createLineString(coordinates);
        line.setSRID(SRID);
        return line;
    }

    public GeoJsonGeometryDto toGeoJson(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        Geometry simplified = geometry.getDimension() == 0
                ? geometry
                : DouglasPeuckerSimplifier.simplify(geometry, MAP_LINE_SIMPLIFY_DEG);
        if (simplified == null || simplified.isEmpty()) {
            simplified = geometry;
        }
        return new GeoJsonGeometryDto(simplified.getGeometryType(), geoJsonCoordinates(simplified));
    }

    /**
     * Parses a GeoJSON geometry DTO (type + coordinates) into a JTS geometry in SRID 4326.
     * Coordinates follow GeoJSON order: [lng, lat].
     */
    public Geometry fromGeoJson(GeoJsonGeometryDto dto) {
        if (dto == null || dto.type() == null || dto.type().isBlank() || dto.coordinates() == null) {
            return null;
        }
        return switch (dto.type()) {
            case "Point" -> pointFromGeoJson(dto.coordinates());
            case "LineString" -> lineStringFromGeoJson(dto.coordinates());
            case "Polygon" -> polygonFromGeoJson(dto.coordinates());
            case "MultiLineString" -> multiLineStringFromGeoJson(dto.coordinates());
            case "MultiPolygon" -> multiPolygonFromGeoJson(dto.coordinates());
            default -> null;
        };
    }

    private Point pointFromGeoJson(Object coordinates) {
        Coordinate coordinate = coordinateFromGeoJson(coordinates);
        if (coordinate == null) {
            return null;
        }
        Point point = geometryFactory.createPoint(coordinate);
        point.setSRID(SRID);
        return point;
    }

    private LineString lineStringFromGeoJson(Object coordinates) {
        Coordinate[] coords = coordinateArrayFromGeoJson(coordinates);
        if (coords == null || coords.length < 2) {
            return null;
        }
        LineString line = geometryFactory.createLineString(coords);
        line.setSRID(SRID);
        return line;
    }

    private MultiLineString multiLineStringFromGeoJson(Object coordinates) {
        if (!(coordinates instanceof List<?> lines) || lines.isEmpty()) {
            return null;
        }
        LineString[] parts = new LineString[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            LineString line = lineStringFromGeoJson(lines.get(i));
            if (line == null) {
                return null;
            }
            parts[i] = line;
        }
        MultiLineString multi = geometryFactory.createMultiLineString(parts);
        multi.setSRID(SRID);
        return multi;
    }

    private Polygon polygonFromGeoJson(Object coordinates) {
        if (!(coordinates instanceof List<?> rings) || rings.isEmpty()) {
            return null;
        }
        LinearRing shell = geometryFactory.createLinearRing(closedCoordinateArray(coordinateArrayFromGeoJson(rings.get(0))));
        if (shell == null || shell.isEmpty()) {
            return null;
        }
        LinearRing[] holes = new LinearRing[Math.max(0, rings.size() - 1)];
        for (int i = 1; i < rings.size(); i++) {
            holes[i - 1] = geometryFactory.createLinearRing(closedCoordinateArray(coordinateArrayFromGeoJson(rings.get(i))));
        }
        Polygon polygon = geometryFactory.createPolygon(shell, holes);
        polygon.setSRID(SRID);
        return polygon;
    }

    private MultiPolygon multiPolygonFromGeoJson(Object coordinates) {
        if (!(coordinates instanceof List<?> polygons) || polygons.isEmpty()) {
            return null;
        }
        Polygon[] parts = new Polygon[polygons.size()];
        for (int i = 0; i < polygons.size(); i++) {
            Polygon polygon = polygonFromGeoJson(polygons.get(i));
            if (polygon == null) {
                return null;
            }
            parts[i] = polygon;
        }
        MultiPolygon multi = geometryFactory.createMultiPolygon(parts);
        multi.setSRID(SRID);
        return multi;
    }

    private Coordinate[] coordinateArrayFromGeoJson(Object coordinates) {
        if (!(coordinates instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Coordinate[] coords = new Coordinate[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Coordinate coordinate = coordinateFromGeoJson(list.get(i));
            if (coordinate == null) {
                return null;
            }
            coords[i] = coordinate;
        }
        return coords;
    }

    private Coordinate[] closedCoordinateArray(Coordinate[] coordinates) {
        if (coordinates == null || coordinates.length == 0) {
            return coordinates;
        }
        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];
        if (first.equals2D(last)) {
            return coordinates;
        }
        Coordinate[] closed = Arrays.copyOf(coordinates, coordinates.length + 1);
        closed[closed.length - 1] = new Coordinate(first);
        return closed;
    }

    private Coordinate coordinateFromGeoJson(Object coordinates) {
        if (!(coordinates instanceof List<?> pair) || pair.size() < 2) {
            return null;
        }
        Double lng = asDouble(pair.get(0));
        Double lat = asDouble(pair.get(1));
        if (lng == null || lat == null) {
            return null;
        }
        return new Coordinate(lng, lat);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object geoJsonCoordinates(Geometry geometry) {
        return switch (geometry) {
            case Point point -> lngLat(point.getCoordinate());
            case LineString lineString -> lineLngLat(lineString);
            case Polygon polygon -> polygonLngLat(polygon);
            case MultiLineString multiLineString -> {
                List<List<double[]>> lines = new ArrayList<>();
                for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                    lines.add(lineLngLat((LineString) multiLineString.getGeometryN(i)));
                }
                yield lines;
            }
            case MultiPolygon multiPolygon -> {
                List<List<List<double[]>>> polygons = new ArrayList<>();
                for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                    polygons.add(polygonLngLat((Polygon) multiPolygon.getGeometryN(i)));
                }
                yield polygons;
            }
            default -> {
                if (geometry.getNumGeometries() == 1) {
                    yield geoJsonCoordinates(geometry.getGeometryN(0));
                }
                List<Object> parts = new ArrayList<>();
                for (int i = 0; i < geometry.getNumGeometries(); i++) {
                    parts.add(geoJsonCoordinates(geometry.getGeometryN(i)));
                }
                yield parts;
            }
        };
    }

    private static List<double[]> lineLngLat(LineString line) {
        List<double[]> coords = new ArrayList<>();
        for (Coordinate coordinate : line.getCoordinates()) {
            coords.add(lngLat(coordinate));
        }
        return coords;
    }

    private static List<List<double[]>> polygonLngLat(Polygon polygon) {
        List<List<double[]>> rings = new ArrayList<>();
        rings.add(lineLngLat(polygon.getExteriorRing()));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(lineLngLat(polygon.getInteriorRingN(i)));
        }
        return rings;
    }

    private static double[] lngLat(Coordinate coordinate) {
        return new double[]{coordinate.getX(), coordinate.getY()};
    }
}
