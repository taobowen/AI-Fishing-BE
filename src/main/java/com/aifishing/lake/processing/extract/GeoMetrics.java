package com.aifishing.lake.processing.extract;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

public final class GeoMetrics {

    private GeoMetrics() {
    }

    public static double metersPerDegreeLat() {
        return 111_320.0;
    }

    public static double metersPerDegreeLng(double latDeg) {
        return 111_320.0 * Math.cos(Math.toRadians(latDeg));
    }

    public static double referenceLat(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return 44.5;
        }
        Point centroid = geometry.getCentroid();
        return centroid.getY();
    }

    public static double areaM2(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return 0;
        }
        double lat = referenceLat(geometry);
        return geometry.getArea() * metersPerDegreeLat() * metersPerDegreeLng(lat);
    }

    public static double lengthM(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return 0;
        }
        double lat = referenceLat(geometry);
        return geometry.getLength() * ((metersPerDegreeLat() + metersPerDegreeLng(lat)) / 2.0);
    }

    public static double distanceM(Geometry a, Geometry b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        double lat = (referenceLat(a) + referenceLat(b)) / 2.0;
        return a.distance(b) * ((metersPerDegreeLat() + metersPerDegreeLng(lat)) / 2.0);
    }

    public static double distanceM(Coordinate a, Coordinate b, double latDeg) {
        double dLat = (a.y - b.y) * metersPerDegreeLat();
        double dLng = (a.x - b.x) * metersPerDegreeLng(latDeg);
        return Math.hypot(dLat, dLng);
    }

    public static double bufferDegrees(double meters, double latDeg) {
        return meters / ((metersPerDegreeLat() + metersPerDegreeLng(latDeg)) / 2.0);
    }

    /**
     * Initial bearing from {@code from} to {@code to} in degrees: 0 = north, 90 = east.
     */
    public static double bearingDegrees(Point from, Point to) {
        if (from == null || to == null) {
            return Double.NaN;
        }
        double lat1 = Math.toRadians(from.getY());
        double lat2 = Math.toRadians(to.getY());
        double dLng = Math.toRadians(to.getX() - from.getX());
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }
}
