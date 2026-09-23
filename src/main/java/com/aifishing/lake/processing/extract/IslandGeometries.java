package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Islands come from imported waterway polygons and from holes already cut into the lake polygon.
 */
public final class IslandGeometries {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    private IslandGeometries() {
    }

    public static List<Geometry> includingInteriorRings(
            List<Geometry> waterwayIslands,
            Geometry water,
            double minAreaM2
    ) {
        List<Geometry> islands = new ArrayList<>();
        if (waterwayIslands != null) {
            for (Geometry island : waterwayIslands) {
                if (island != null && !island.isEmpty()) {
                    Geometry copy = island.copy();
                    copy.setSRID(GeoMapper.SRID);
                    islands.add(copy);
                }
            }
        }
        if (water == null || water.isEmpty()) {
            return islands;
        }
        for (Polygon polygon : polygons(water)) {
            for (int ringIndex = 0; ringIndex < polygon.getNumInteriorRing(); ringIndex++) {
                LinearRing ring = polygon.getInteriorRingN(ringIndex);
                if (ring == null || ring.getNumPoints() < 4) {
                    continue;
                }
                Polygon hole = FACTORY.createPolygon(ring);
                hole.setSRID(GeoMapper.SRID);
                if (GeoMetrics.areaM2(hole) < minAreaM2) {
                    continue;
                }
                if (alreadyCovered(islands, hole)) {
                    continue;
                }
                islands.add(hole);
            }
        }
        return islands;
    }

    private static boolean alreadyCovered(List<Geometry> islands, Geometry hole) {
        double holeArea = GeoMetrics.areaM2(hole);
        if (holeArea <= 0) {
            return true;
        }
        for (Geometry island : islands) {
            try {
                if (island.covers(hole)) {
                    return true;
                }
                if (!island.intersects(hole)) {
                    continue;
                }
                Geometry overlap = island.intersection(hole);
                if (overlap != null && GeoMetrics.areaM2(overlap) >= holeArea * 0.5) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // a bad waterway polygon does not hide a boundary hole
            }
        }
        return false;
    }

    private static List<Polygon> polygons(Geometry geometry) {
        List<Polygon> polygons = new ArrayList<>();
        collect(geometry, polygons);
        return polygons;
    }

    private static void collect(Geometry geometry, List<Polygon> polygons) {
        if (geometry instanceof Polygon polygon) {
            polygons.add(polygon);
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            collect(geometry.getGeometryN(i), polygons);
        }
    }
}
