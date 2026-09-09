package com.aifishing.lake.ingestion.processor;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GeoJsonFeatureParser {

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public GeoJsonFeatureParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ParsedFeature> parse(byte[] geoJson) {
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            if (root.has("error")) {
                throw new IllegalArgumentException("ArcGIS error payload: " + root.get("error"));
            }
            JsonNode features = root.path("features");
            List<ParsedFeature> parsed = new ArrayList<>();
            if (!features.isArray()) {
                return parsed;
            }
            for (JsonNode feature : features) {
                Geometry geometry = toGeometry(feature.get("geometry"));
                if (geometry != null && (geometry.isEmpty() || !geometry.isValid())) {
                    throw new IllegalArgumentException("Empty or invalid geometry in GeoJSON feature");
                }
                if (geometry != null) {
                    geometry.setSRID(GeoMapper.SRID);
                }
                JsonNode properties = feature.path("properties");
                String sourceId = firstText(properties, "OGF_ID", "OBJECTID", "FID", "id");
                if (sourceId == null && feature.hasNonNull("id")) {
                    sourceId = feature.get("id").asText();
                }
                parsed.add(new ParsedFeature(sourceId, properties, geometry));
            }
            return parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid GeoJSON", ex);
        }
    }

    public boolean exceededTransferLimit(byte[] geoJson) {
        try {
            return objectMapper.readTree(geoJson).path("exceededTransferLimit").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private Geometry toGeometry(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String type = node.path("type").asText();
        JsonNode coordinates = node.get("coordinates");
        return switch (type) {
            case "Point" -> point(coordinates);
            case "LineString" -> lineString(coordinates);
            case "MultiLineString" -> multiLineString(coordinates);
            case "Polygon" -> polygon(coordinates);
            case "MultiPolygon" -> multiPolygon(coordinates);
            default -> null;
        };
    }

    private Point point(JsonNode coordinates) {
        Coordinate coordinate = coordinate(coordinates);
        Point point = geometryFactory.createPoint(coordinate);
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    private LineString lineString(JsonNode coordinates) {
        LineString line = geometryFactory.createLineString(coordinates(coordinates));
        line.setSRID(GeoMapper.SRID);
        return line;
    }

    private MultiLineString multiLineString(JsonNode coordinates) {
        LineString[] lines = new LineString[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            lines[i] = lineString(coordinates.get(i));
        }
        MultiLineString geometry = geometryFactory.createMultiLineString(lines);
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }

    private Polygon polygon(JsonNode coordinates) {
        LinearRing shell = geometryFactory.createLinearRing(coordinates(coordinates.get(0)));
        LinearRing[] holes = new LinearRing[Math.max(0, coordinates.size() - 1)];
        for (int i = 1; i < coordinates.size(); i++) {
            holes[i - 1] = geometryFactory.createLinearRing(coordinates(coordinates.get(i)));
        }
        Polygon polygon = geometryFactory.createPolygon(shell, holes);
        polygon.setSRID(GeoMapper.SRID);
        return polygon;
    }

    private MultiPolygon multiPolygon(JsonNode coordinates) {
        Polygon[] polygons = new Polygon[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            polygons[i] = polygon(coordinates.get(i));
        }
        MultiPolygon geometry = geometryFactory.createMultiPolygon(polygons);
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }

    private Coordinate[] coordinates(JsonNode array) {
        Coordinate[] result = new Coordinate[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = coordinate(array.get(i));
        }
        return result;
    }

    private Coordinate coordinate(JsonNode array) {
        return new Coordinate(array.get(0).asDouble(), array.get(1).asDouble());
    }

    private String firstText(JsonNode properties, String... names) {
        for (String name : names) {
            if (properties.hasNonNull(name)) {
                return properties.get(name).asText();
            }
        }
        return null;
    }
}
