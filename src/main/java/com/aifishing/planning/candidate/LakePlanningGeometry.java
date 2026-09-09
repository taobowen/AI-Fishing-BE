package com.aifishing.planning.candidate;

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
        this.water = water;
        this.islands = islands == null ? List.of() : List.copyOf(islands);
        this.preparedWater = hasGeometry(water) ? PreparedGeometryFactory.prepare(water) : null;
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

    public List<Geometry> islandsCopy() {
        return new ArrayList<>(islands);
    }

    private static boolean hasGeometry(Geometry geometry) {
        return geometry != null && !geometry.isEmpty();
    }
}
