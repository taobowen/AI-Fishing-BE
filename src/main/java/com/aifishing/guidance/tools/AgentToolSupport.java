package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;

/**
 * Shared envelope helpers. Never puts stack traces or exception text into {@code data}.
 */
final class AgentToolSupport {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private AgentToolSupport() {
    }

    static <T> Parsed<T> parse(
            ToolRequestEnvelope request,
            ToolName name,
            String defName,
            Class<T> type,
            Clock clock
    ) {
        if (request == null || request.arguments() == null
                || request.arguments().isNull()
                || request.arguments().isMissingNode()
                || !request.arguments().isObject()) {
            return Parsed.invalid(invalid(name, clock, "arguments"));
        }
        Set<ValidationMessage> errors = GuidanceContracts.schema(defName).validate(request.arguments());
        if (!errors.isEmpty()) {
            return Parsed.invalid(invalid(name, clock, fieldOf(errors)));
        }
        try {
            T params = GuidanceContracts.mapper().treeToValue(request.arguments(), type);
            if (params == null) {
                return Parsed.invalid(invalid(name, clock, "arguments"));
            }
            return Parsed.ok(params);
        } catch (Exception ex) {
            return Parsed.invalid(invalid(name, clock, "arguments"));
        }
    }

    static ToolResultEnvelope ok(ToolName name, Clock clock, JsonNode data) {
        if (ToolResultSanitizer.isEmptyData(data)) {
            return unknown(name, clock);
        }
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.OK,
                clock.instant(),
                name.wire(),
                data,
                null,
                null,
                null
        );
    }

    static ToolResultEnvelope unknown(ToolName name, Clock clock) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.UNKNOWN,
                clock.instant(),
                name.wire(),
                null,
                null,
                null,
                null
        );
    }

    static ToolResultEnvelope invalid(ToolName name, Clock clock, String field) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.INVALID_DATA,
                clock.instant(),
                name.wire(),
                null,
                null,
                null,
                field == null || field.isBlank() ? "arguments" : field
        );
    }

    static ToolResultEnvelope error(ToolName name, Clock clock) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.ERROR,
                clock.instant(),
                name.wire(),
                null,
                ToolErrorType.INTERNAL,
                Boolean.TRUE,
                null
        );
    }

    static Point point(double latitudeWgs84, double longitudeWgs84) {
        Point point = FACTORY.createPoint(new Coordinate(longitudeWgs84, latitudeWgs84));
        point.setSRID(4326);
        return point;
    }

    static Double latitudeOf(Geometry geometry) {
        Point point = pointOf(geometry);
        return point == null ? null : point.getY();
    }

    static Double longitudeOf(Geometry geometry) {
        Point point = pointOf(geometry);
        return point == null ? null : point.getX();
    }

    static Point pointOf(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Point point) {
            return point;
        }
        Point centroid = geometry.getCentroid();
        return centroid == null || centroid.isEmpty() ? null : centroid;
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double dLat = latRad2 - latRad1;
        double dLon = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double a = sinLat * sinLat + Math.cos(latRad1) * Math.cos(latRad2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    static String fieldOf(Set<ValidationMessage> errors) {
        if (errors == null) {
            return "arguments";
        }
        for (ValidationMessage error : errors) {
            if (error == null || error.getInstanceLocation() == null) {
                continue;
            }
            String path = error.getInstanceLocation().toString();
            if (path == null || path.isBlank() || "/".equals(path)) {
                continue;
            }
            String trimmed = path.startsWith("/") ? path.substring(1) : path;
            int slash = trimmed.indexOf('/');
            String field = slash < 0 ? trimmed : trimmed.substring(0, slash);
            if (!field.isBlank()) {
                return field;
            }
        }
        return "arguments";
    }

    record Parsed<T>(T params, ToolResultEnvelope error) {
        static <T> Parsed<T> ok(T params) {
            return new Parsed<>(params, null);
        }

        static <T> Parsed<T> invalid(ToolResultEnvelope error) {
            return new Parsed<>(null, error);
        }

        boolean valid() {
            return error == null && params != null;
        }
    }
}
