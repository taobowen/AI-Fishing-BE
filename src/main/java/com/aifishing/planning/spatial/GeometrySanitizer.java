package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeometrySanitizer {

    private GeometrySanitizer() {
    }

    public static Geometry validateFixAndNormalize(Geometry geometry, double sliverMinAreaM2) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        Geometry working = geometry.copy();
        working.setSRID(GeoMapper.SRID);
        if (!working.isValid()) {
            working = GeometryFixer.fix(working);
        }
        if (working == null || working.isEmpty()) {
            return null;
        }
        try {
            working = working.buffer(0);
        } catch (Exception ignored) {
            working = GeometryFixer.fix(working);
        }
        if (working == null || working.isEmpty() || !working.isValid()) {
            working = GeometryFixer.fix(working);
        }
        if (working == null || working.isEmpty()) {
            return null;
        }
        working = dropSlivers(working, sliverMinAreaM2);
        if (working == null || working.isEmpty()) {
            return null;
        }
        GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(new PrecisionModel(1e7));
        reducer.setChangePrecisionModel(true);
        working = reducer.reduce(working);
        working.normalize();
        working.setSRID(GeoMapper.SRID);
        if (!working.isValid() || working.isEmpty()) {
            return null;
        }
        return working;
    }

    public static Geometry dropSlivers(Geometry geometry, double sliverMinAreaM2) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        if (geometry.getDimension() < 2) {
            return geometry;
        }
        List<Polygon> kept = new ArrayList<>();
        collectPolygons(geometry, kept, sliverMinAreaM2);
        if (kept.isEmpty()) {
            return null;
        }
        kept.sort(Comparator.comparingDouble((Polygon polygon) -> -polygon.getArea()));
        if (kept.size() == 1) {
            Polygon polygon = kept.get(0);
            polygon.setSRID(GeoMapper.SRID);
            return polygon;
        }
        MultiPolygon multi = geometry.getFactory().createMultiPolygon(kept.toArray(Polygon[]::new));
        multi.setSRID(GeoMapper.SRID);
        return multi;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> kept, double sliverMinAreaM2) {
        if (geometry instanceof Polygon polygon) {
            if (GeoMetrics.areaM2(polygon) >= sliverMinAreaM2) {
                kept.add(polygon);
            }
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                collectPolygons(collection.getGeometryN(i), kept, sliverMinAreaM2);
            }
        }
    }
}
