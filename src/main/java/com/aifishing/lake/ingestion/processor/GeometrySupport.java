package com.aifishing.lake.ingestion.processor;

import com.aifishing.common.geo.GeoMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public final class GeometrySupport {

    private GeometrySupport() {
    }

    public static void requireValid(Geometry geometry, String context) {
        if (geometry == null || geometry.isEmpty() || !geometry.isValid()) {
            throw new IllegalArgumentException("Invalid geometry for " + context);
        }
        if (geometry.getSRID() == 0) {
            geometry.setSRID(GeoMapper.SRID);
        }
        if (geometry.getSRID() != GeoMapper.SRID) {
            throw new IllegalArgumentException("Geometry SRID must be 4326 for " + context);
        }
    }

    public static Point asPoint(Geometry geometry, String context) {
        requireValid(geometry, context);
        if (geometry instanceof Point point) {
            return point;
        }
        throw new IllegalArgumentException("Expected Point for " + context + ", got " + geometry.getGeometryType());
    }

    public static MultiLineString asMultiLineString(Geometry geometry, String context) {
        requireValid(geometry, context);
        if (geometry instanceof MultiLineString multiLineString) {
            return multiLineString;
        }
        if (geometry instanceof LineString lineString) {
            MultiLineString wrapped = geometry.getFactory().createMultiLineString(new LineString[]{lineString});
            wrapped.setSRID(geometry.getSRID());
            return wrapped;
        }
        throw new IllegalArgumentException("Expected line geometry for " + context + ", got " + geometry.getGeometryType());
    }

    public static MultiPolygon asMultiPolygon(Geometry geometry, String context) {
        requireValid(geometry, context);
        if (geometry instanceof MultiPolygon multiPolygon) {
            return multiPolygon;
        }
        if (geometry instanceof Polygon polygon) {
            MultiPolygon wrapped = geometry.getFactory().createMultiPolygon(new Polygon[]{polygon});
            wrapped.setSRID(geometry.getSRID());
            return wrapped;
        }
        throw new IllegalArgumentException("Expected polygon geometry for " + context + ", got " + geometry.getGeometryType());
    }
}
