package com.aifishing.planning.candidate;

import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.spatial.RequestSpatialCache;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;

public final class LakePlanningGeometry {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final Geometry water;
    private final List<Geometry> islands;
    private final PreparedGeometry preparedWater;
    private final List<PreparedGeometry> preparedIslands;

    public LakePlanningGeometry(Geometry water, List<Geometry> islands) {
        this.water = PolygonalGeometries.of(water);
        this.islands = islands == null ? List.of() : islands.stream()
                .map(PolygonalGeometries::of)
                .filter(geometry -> geometry != null && !geometry.isEmpty())
                .toList();
        this.preparedWater = hasGeometry(this.water) ? PreparedGeometryFactory.prepare(this.water) : null;
        List<PreparedGeometry> prepared = new ArrayList<>(this.islands.size());
        for (Geometry island : this.islands) {
            prepared.add(hasGeometry(island) ? PreparedGeometryFactory.prepare(island) : null);
        }
        this.preparedIslands = List.copyOf(prepared);
    }

    public Geometry water() {
        return water;
    }

    public List<Geometry> islands() {
        return islands;
    }

    public boolean hasWater() {
        return hasGeometry(water);
    }

    public boolean inWater(Point point) {
        if (point == null || point.isEmpty() || preparedWater == null) {
            return false;
        }
        return preparedWater.covers(point);
    }

    public boolean onIsland(Point point) {
        if (point == null || point.isEmpty()) {
            return false;
        }
        for (PreparedGeometry island : preparedIslands) {
            if (island != null && island.covers(point)) {
                return true;
            }
        }
        return false;
    }

    public boolean validFishingPoint(Point point) {
        return inWater(point) && !onIsland(point);
    }

    public boolean landCrossing(Point from, Point to) {
        if (from == null || to == null) {
            return false;
        }
        RequestSpatialCache spatial = RequestSpatialCache.current();
        if (spatial != null) {
            Boolean cached = spatial.landCrossing(from, to);
            if (cached != null) {
                return cached;
            }
        }
        boolean crossing = landCrossingUncached(from, to);
        if (spatial != null) {
            spatial.storeLandCrossing(from, to, crossing);
        }
        return crossing;
    }

    private boolean landCrossingUncached(Point from, Point to) {
        LineString segment = FACTORY.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                from.getCoordinate(),
                to.getCoordinate()
        });
        segment.setSRID(4326);
        if (preparedWater != null && !preparedWater.covers(segment)) {
            return true;
        }
        for (int i = 0; i < preparedIslands.size(); i++) {
            PreparedGeometry prepared = preparedIslands.get(i);
            Geometry island = islands.get(i);
            if (prepared != null && prepared.intersects(segment) && !island.touches(segment)) {
                return true;
            }
        }
        return false;
    }

    public boolean crossesIsland(Point from, Point to) {
        if (from == null || to == null || GeoMetrics.distanceM(from, to) <= 1) {
            return false;
        }
        LineString segment = FACTORY.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                from.getCoordinate(),
                to.getCoordinate()
        });
        segment.setSRID(4326);
        for (int i = 0; i < preparedIslands.size(); i++) {
            PreparedGeometry prepared = preparedIslands.get(i);
            Geometry island = islands.get(i);
            if (prepared != null && prepared.intersects(segment) && !island.touches(segment)) {
                return true;
            }
        }
        return false;
    }

    public List<Geometry> islandsCopy() {
        return new ArrayList<>(islands);
    }

    private static boolean hasGeometry(Geometry geometry) {
        return geometry != null && !geometry.isEmpty();
    }
}
