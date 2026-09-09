package com.aifishing.lake.processing.admin;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeoJsonGeometryWriter {

    private GeoJsonGeometryWriter() {
    }

    public static Map<String, Object> toGeoJson(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return Map.of("type", "GeometryCollection", "geometries", List.of());
        }
        return switch (geometry.getGeometryType()) {
            case "Point" -> point((Point) geometry);
            case "MultiPoint" -> multiPoint((MultiPoint) geometry);
            case "LineString", "LinearRing" -> lineString((LineString) geometry);
            case "MultiLineString" -> multiLineString((MultiLineString) geometry);
            case "Polygon" -> polygon((Polygon) geometry);
            case "MultiPolygon" -> multiPolygon((MultiPolygon) geometry);
            default -> {
                List<Map<String, Object>> geometries = new ArrayList<>();
                for (int i = 0; i < geometry.getNumGeometries(); i++) {
                    geometries.add(toGeoJson(geometry.getGeometryN(i)));
                }
                yield Map.of("type", "GeometryCollection", "geometries", geometries);
            }
        };
    }

    private static Map<String, Object> point(Point point) {
        return Map.of("type", "Point", "coordinates", position(point.getCoordinate()));
    }

    private static Map<String, Object> multiPoint(MultiPoint multiPoint) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
            coordinates.add(position(multiPoint.getGeometryN(i).getCoordinate()));
        }
        return Map.of("type", "MultiPoint", "coordinates", coordinates);
    }

    private static Map<String, Object> lineString(LineString lineString) {
        return Map.of("type", "LineString", "coordinates", line(lineString));
    }

    private static Map<String, Object> multiLineString(MultiLineString multiLineString) {
        List<List<List<Double>>> coordinates = new ArrayList<>();
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            coordinates.add(line((LineString) multiLineString.getGeometryN(i)));
        }
        return Map.of("type", "MultiLineString", "coordinates", coordinates);
    }

    private static Map<String, Object> polygon(Polygon polygon) {
        List<List<List<Double>>> rings = new ArrayList<>();
        rings.add(line(polygon.getExteriorRing()));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(line(polygon.getInteriorRingN(i)));
        }
        return Map.of("type", "Polygon", "coordinates", rings);
    }

    private static Map<String, Object> multiPolygon(MultiPolygon multiPolygon) {
        List<Object> coordinates = new ArrayList<>();
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            coordinates.add(polygon((Polygon) multiPolygon.getGeometryN(i)).get("coordinates"));
        }
        return Map.of("type", "MultiPolygon", "coordinates", coordinates);
    }

    private static List<List<Double>> line(LineString lineString) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (Coordinate coordinate : lineString.getCoordinates()) {
            coordinates.add(position(coordinate));
        }
        return coordinates;
    }

    private static List<Double> position(Coordinate coordinate) {
        List<Double> position = new ArrayList<>();
        position.add(coordinate.x);
        position.add(coordinate.y);
        return position;
    }

    public static Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }
}
