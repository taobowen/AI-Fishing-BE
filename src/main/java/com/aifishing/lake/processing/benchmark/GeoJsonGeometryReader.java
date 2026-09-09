package com.aifishing.lake.processing.benchmark;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GeoJsonGeometryReader {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    private GeoJsonGeometryReader() {
    }

    public static Geometry read(JsonNode geometry) {
        if (geometry == null || geometry.isMissingNode() || geometry.isNull()) {
            return null;
        }
        String type = geometry.path("type").asText("");
        JsonNode coordinates = geometry.get("coordinates");
        Geometry result = switch (type) {
            case "Point" -> point(coordinates);
            case "LineString" -> line(coordinates);
            case "Polygon" -> polygon(coordinates);
            case "MultiPolygon" -> {
                Polygon first = polygon(coordinates.path(0));
                yield first;
            }
            default -> null;
        };
        if (result != null) {
            result.setSRID(GeoMapper.SRID);
        }
        return result;
    }

    public static FeatureType featureType(JsonNode properties) {
        if (properties == null) {
            return null;
        }
        String raw = properties.path("type").asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FeatureType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Point point(JsonNode coordinates) {
        Coordinate coordinate = coordinate(coordinates);
        if (coordinate == null) {
            return null;
        }
        return FACTORY.createPoint(coordinate);
    }

    private static LineString line(JsonNode coordinates) {
        Coordinate[] coords = ring(coordinates, false);
        if (coords.length < 2) {
            return null;
        }
        return FACTORY.createLineString(coords);
    }

    private static Polygon polygon(JsonNode coordinates) {
        JsonNode ringNode = coordinates;
        if (coordinates != null && coordinates.isArray() && coordinates.size() > 0 && coordinates.get(0).isArray()
                && coordinates.get(0).size() > 0 && coordinates.get(0).get(0).isArray()) {
            ringNode = coordinates.get(0);
        }
        Coordinate[] coords = ring(ringNode, true);
        if (coords.length < 4) {
            return null;
        }
        LinearRing ring = FACTORY.createLinearRing(coords);
        return FACTORY.createPolygon(ring);
    }

    private static Coordinate[] ring(JsonNode coordinates, boolean close) {
        if (coordinates == null || !coordinates.isArray()) {
            return new Coordinate[0];
        }
        List<Coordinate> list = new ArrayList<>();
        for (JsonNode node : coordinates) {
            Coordinate coordinate = coordinate(node);
            if (coordinate != null) {
                list.add(coordinate);
            }
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

    private static Coordinate coordinate(JsonNode node) {
        if (node == null || !node.isArray() || node.size() < 2) {
            return null;
        }
        return new Coordinate(node.get(0).asDouble(), node.get(1).asDouble());
    }
}
