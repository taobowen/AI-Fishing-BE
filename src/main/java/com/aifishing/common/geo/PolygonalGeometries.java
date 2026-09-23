package com.aifishing.common.geo;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * JTS overlay/buffer reject generic {@link GeometryCollection} (mixed leftover
 * lines/points from union). Flatten to Polygon/MultiPolygon before those ops.
 */
public final class PolygonalGeometries {

    private PolygonalGeometries() {
    }

    public static Geometry of(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
            return geometry;
        }
        List<Polygon> polygons = new ArrayList<>();
        collectPolygons(geometry, polygons);
        if (polygons.isEmpty()) {
            return null;
        }
        int srid = geometry.getSRID() == 0 ? GeoMapper.SRID : geometry.getSRID();
        if (polygons.size() == 1) {
            Polygon polygon = polygons.get(0);
            polygon.setSRID(srid);
            return polygon;
        }
        MultiPolygon multi = geometry.getFactory().createMultiPolygon(polygons.toArray(Polygon[]::new));
        multi.setSRID(srid);
        return multi;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> polygons) {
        if (geometry instanceof Polygon polygon) {
            if (!polygon.isEmpty()) {
                polygons.add(polygon);
            }
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                collectPolygons(collection.getGeometryN(i), polygons);
            }
        }
    }
}
