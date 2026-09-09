package com.aifishing.lake.processing.vision;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.render.GeorefTransform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class VisionFeatureParser {

    private final ObjectMapper objectMapper;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public VisionFeatureParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<VisionCandidate> parse(String rawJson, GeorefTransform georef) {
        JsonNode root = read(rawJson);
        JsonNode features = root.path("features");
        if (!features.isArray()) {
            return List.of();
        }
        List<VisionCandidate> candidates = new ArrayList<>();
        for (JsonNode feature : features) {
            FeatureType type = parseType(feature.path("type").asText(null));
            if (type == null) {
                continue;
            }
            Geometry geometry = geometry(feature, georef, type);
            if (geometry == null || geometry.isEmpty()) {
                continue;
            }
            geometry.setSRID(GeoMapper.SRID);
            Double confidence = feature.has("confidence") && feature.get("confidence").isNumber()
                    ? feature.get("confidence").asDouble()
                    : null;
            candidates.add(new VisionCandidate(type, geometry, confidence, feature.path("evidence").asText(null)));
        }
        return candidates;
    }

    private JsonNode read(String rawJson) {
        try {
            return objectMapper.readTree(extractJsonObject(rawJson));
        } catch (Exception ex) {
            throw new IllegalStateException("Vision response was not valid JSON", ex);
        }
    }

    static String extractJsonObject(String rawJson) {
        String json = rawJson == null ? "{}" : rawJson.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return json.substring(start, end + 1);
        }
        return json.isBlank() ? "{}" : json;
    }

    private FeatureType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FeatureType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Geometry geometry(JsonNode feature, GeorefTransform georef, FeatureType type) {
        String geometryType = feature.path("geometryType").asText("");
        JsonNode coordinates = feature.get("coordinates");
        if (coordinates == null || coordinates.isMissingNode()) {
            return null;
        }
        if (type == FeatureType.POINT || "Point".equalsIgnoreCase(geometryType)) {
            return point(coordinates, georef);
        }
        if (type == FeatureType.DROP_OFF || "LineString".equalsIgnoreCase(geometryType)) {
            return line(coordinates, georef);
        }
        return polygon(coordinates, georef);
    }

    private Point point(JsonNode coordinates, GeorefTransform georef) {
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }
        return georef.toWgs84(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
    }

    private LineString line(JsonNode coordinates, GeorefTransform georef) {
        Coordinate[] coords = ring(coordinates, georef, false);
        if (coords.length < 2) {
            return null;
        }
        LineString line = factory.createLineString(coords);
        line.setSRID(GeoMapper.SRID);
        return line;
    }

    private Geometry polygon(JsonNode coordinates, GeorefTransform georef) {
        JsonNode ringNode = coordinates;
        if (coordinates.isArray() && coordinates.size() > 0 && coordinates.get(0).isArray()
                && coordinates.get(0).size() > 0 && coordinates.get(0).get(0).isArray()) {
            ringNode = coordinates.get(0);
        }
        Coordinate[] coords = ring(ringNode, georef, true);
        if (coords.length < 4) {
            return null;
        }
        LinearRing ring = factory.createLinearRing(coords);
        Polygon polygon = factory.createPolygon(ring);
        polygon.setSRID(GeoMapper.SRID);
        Geometry geometry = polygon.isValid() ? polygon : polygon.buffer(0);
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }

    private Coordinate[] ring(JsonNode coordinates, GeorefTransform georef, boolean close) {
        if (coordinates == null || !coordinates.isArray()) {
            return new Coordinate[0];
        }
        List<Coordinate> list = new ArrayList<>();
        for (JsonNode node : coordinates) {
            if (!node.isArray() || node.size() < 2) {
                continue;
            }
            Point point = georef.toWgs84(node.get(0).asDouble(), node.get(1).asDouble());
            list.add(new Coordinate(point.getX(), point.getY()));
        }
        if (close && list.size() >= 3) {
            Coordinate first = list.get(0);
            Coordinate last = list.get(list.size() - 1);
            if (!first.equals2D(last)) {
                list.add(new Coordinate(first));
            }
        }
        return list.toArray(Coordinate[]::new);
    }
}
